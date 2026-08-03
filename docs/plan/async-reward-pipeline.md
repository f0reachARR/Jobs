# 報酬パイプラインの非同期化

現在の `pipeline.RewardPipeline` は段階 1 から 12 までを listener の中で main thread 同期実行する。
本文書は、段階 4 から 11 を単一のワーカースレッドへ移す設計と、そのために必要な変更を実装順にまとめる。

## 現状と制約

main thread から外せない段階は 2 つある。

段階 3 の自動化対策は Bukkit の状態を直接読む。
`UnplantedCropCheck` と `RecentlyPlacedBreakCheck` は `Block#getBlockData` と `Block#getState` を経由して PDC を読み、`SpawnerOriginCheck` は `Entity#getEntitySpawnReason` を呼ぶ。
さらに `detection.DetectionSubject` が生の `Block` と `Entity` 参照を保持しており、これらは同 tick 内でしか安全に触れない。

段階 12 の `revokeCriteria` も main thread 専用である。

一方、非同期化を阻む可変状態が 2 つある。

- **`modifier.dailycap.DailyTotalCache`**：`HashMap` で当日累計を保持し、main thread 専用と明記されている。
- **`modifier.variety.VarietyRingBuffer`**：直近 window 件のキー列を保持し、同じく main thread 専用と明記されている。

この 2 つは単にスレッドセーフでないだけでなく、同一プレイヤー内の実行順序に依存する。
variety の比率は「今回のアクションを含めない直近 window 件」から求め、daily_cap は累計の read-modify-write を行う。
複数スレッドで同じプレイヤーの 2 件が同時に走ると、比率が狂い、上限を超えて支払う。

## 設計

### 実行モデル

listener が呼ぶ prologue だけを main thread で同期実行し、残りを**単一のワーカースレッド**へ渡す。

```
main   : 段階 1 Matcher → 2 専業判定 → 3 自動化対策 → 12 revokeCriteria
         （listener の中でインラインに実行。Block / Entity 参照はここで使い切る）
   ↓ Bukkit 依存を切り離した ctx を境界キューへ enqueue
worker : 段階 4 基礎報酬 → 5 rare → 6 内蔵 Modifier → 7 拡張 Modifier
         → 8 Splitter → 9 丸め → 10 送金 → 11 行動ログ
   ↓ Bukkit API を要する副作用のみ fire-and-forget で投げ返す
main   : rare の broadcast、economy_on_main が true のときの送金
```

ワーカーを 1 本に絞ると、段階 4 から 11 の可変状態の書き手が 1 スレッドだけになる。
プレイヤー単位で実行を直列化する仕組みも、プレイヤーごとの未処理件数を数える仕組みも不要になる。
実行順序はグローバルな FIFO になり、同一プレイヤーの直列性はその特別な場合として自動的に満たされる。

境界キューと単一 daemon スレッドという構成は、`persistence.async.ActionLogWriteQueue` と `persistence.async.BatchFlushWorker` が既に採っている型と同じである。
容量、drop 時の警告、`drain(timeout)` の意味論をそのまま流用できる。

### main thread への投げ返し

ワーカーは main thread の完了を待たない。
`Bukkit.getScheduler().runTask` は呼び出し側をブロックしないため、既存の `AsyncExecutor#runOnMain` へ fire-and-forget で投げて次のタスクへ進む。

待たないので、ワーカーのスループットは tick レートに縛られない。
`CompletableFuture` の chain も、main thread の完了を future で受け取る新 API も要らない。

投げ返す対象は 2 つに限られる。

- `RareRollStage` の `Bukkit.broadcast`。
- `reward.async.economy_on_main` が true のときの Vault 送金。

送金を投げ返す場合、送金の成否が確定するのは段階 11 の行動ログ書き込みより後になりうる。
`spec/04-reward-pipeline.md` の「送金失敗は致命扱い」を、行動ログへの負号書き込みではなく SEVERE ログと運営通知に一本化する。

`economy_on_main` が true のとき、送金 1 件ごとに `runTask` を積むと問題が出る。
`runTask` でスケジュールしたタスクは次の tick でまとめて実行されるため、キューに数万件が溜まっている状態では 1 tick に数万件の送金が走り、サーバが固まる。

そこで送金だけは main thread 側の受け皿を経由させる。
`MainWorkQueue`（`ConcurrentLinkedQueue`）にワーカーが積み、`runTaskTimer` で毎 tick 起きるドレイナが `main_work_per_tick` 件まで取り出して実行する。
tick あたりの上限があるので、キューがどれだけ深くても 1 tick の滞在時間は一定に収まる。

rare の broadcast は発生頻度が低いので、`runOnMain` へ直接投げる。

### 段階 12 の前倒し

段階 12 を prologue の末尾へ移す。
ワーカーを経由すると advancement の再発火が数 tick 遅れ、その間のイベントを取りこぼすためである。

`revokeCriteria` は報酬の算出結果に依存しないので、位置を移しても意味論は変わらない。
prologue で HALT する段階は 1 から 3 に限られるため、前倒ししても「どの場合に revoke が走るか」は現状と一致する。

### 境界キューの容量

drop を減らす主な手段はキューを十分大きく取ることである。
既存の `ActionLogWriteQueue` が 100,000 件なので、報酬キューも同じ 100,000 件を既定とする。

`ArrayBlockingQueue` ではなく `LinkedBlockingQueue(capacity)` を使う。
`ArrayBlockingQueue` は容量分の配列を起動時に確保するため、100,000 件で 800 KB を常に占める。
`LinkedBlockingQueue` は要素ごとにノードを確保するので、平常時（キュー深度がほぼ 0）のメモリコストが容量に比例しない。

1 件あたりの実メモリは、`PipelineContext` 本体が約 130 バイト、`zeroReasons` の `ArrayList` が約 40 バイト、キューのノードが 24 バイトで、合わせて 200 バイト前後になる。
100,000 件で 20 MB 程度である。
`zeroReasons` は自動化対策に該当したときしか使わないので、遅延生成にして平常時の取り分を削る。

容量を上げるだけでは足りない点が 2 つある。

第一に、深いキューは `Player` 参照を掴んだままになる。
`PipelineContext` が強参照で `Player` を持つと、ログアウト後もエンティティとワールドのグラフが解放されない。
`VaultEconomyAdapter#deposit` は既に `OfflinePlayer` を受け取るので、境界で `Player` の強参照を捨て、送金時に `Bukkit.getOfflinePlayer(uuid)` で引き直す。
UUID 指定の `getOfflinePlayer` はブロッキング検索を行わないので、ワーカーから呼んで差し支えない。

第二に、容量が吸収できるのはバーストだけである。
到着レートがワーカーのスループットを恒常的に上回る状態では、容量を増やしても drop が遅延に置き換わるだけで、最後は同じところへ行き着く。
恒常的な過負荷を drop が起きる前に運用者へ見せる必要がある。

- キュー深度が容量の 50% と 80% を超えたら WARNING を出す。同じ水位で出し続けないよう、30 秒に 1 回へ絞る。
- `/jobs admin` から現在のキュー深度、累計 drop 件数、直近のワーカー処理レートを読めるようにする。

そのうえで溢れた場合は drop する。
main thread をブロックすればサーバが止まり、main thread でインライン実行すれば可変状態の書き手が 2 本になって単一ワーカーの前提が崩れる。
どちらも避ける。

### Stage の thread affinity

`pipeline.Stage` に実行スレッドの宣言を足す。

```java
public interface Stage {
    enum Result { CONTINUE, HALT }
    enum Affinity { MAIN, WORKER }

    Result execute(PipelineContext ctx);

    /** この Stage を走らせるスレッド。既定は MAIN。 */
    default Affinity affinity() { return Affinity.MAIN; }
}
```

`RewardPipeline` は wiring 時に `List<Stage>` を prologue と worker の 2 ブロックへ分割する。
実行時は prologue を呼び出しスレッドで直接実行し、HALT が返らなければ worker ブロックを境界キューへ積む。
待ち合わせが無いので、HALT の伝播にフラグは要らず、各ブロック内の早期 return で足りる。

`reward.async.enabled: false` のときは分割せず、全段階を呼び出しスレッドで同期実行する。

`EconomyTransferStage` の affinity は WORKER のままで、`economy_on_main` が true のときだけ `deposit` 呼び出しを `runOnMain` へ包む。

### PipelineContext の非同期安全化

prologue からワーカーへ渡る境界で、Bukkit 依存を落とす。

- `detachBukkitRefs()` を追加し、`subject` を `DetectionSubject.empty()` に差し替える。段階 4 以降が `Block` と `Entity` に触れないことをコードで保証する。
- `playerUuid` と `playerName` を prologue で確定して保持する。
- bypass 権限 4 種（`BYPASS_SPECIALTY`, `BYPASS_ANTI_AUTOMATION`, `BYPASS_VARIETY_PENALTY`, `BYPASS_DAILY_CAP`）を prologue で解決し、boolean として持つ。`Player#hasPermission` をワーカーから呼ばずに済み、パイプライン全体で判定が一貫する。
- `Player` の強参照を捨てる。段階 10 の送金は `Bukkit.getOfflinePlayer(uuid)` で引き直し、拡張 API の `getPlayer()` は `Bukkit.getPlayer(uuid)` を都度引く。オフラインなら null を返す nullable 契約になる。

`SpecialtyStage` と `AntiAutomationStage` と `BuiltinModifierStage` の `hasPermission` 呼び出しを、この boolean 参照へ置き換える。

### 可変状態の扱い

書き手はワーカー 1 本に限られるが、読み手は main thread にも居る。
UI と `/jobs status` と PlaceholderAPI が `VarietyPenaltyEvaluator#snapshot` と `DailyTotalCache#todayTotal` を main thread から読む。

`HashMap` は「単一書き手 + 別スレッドからの読み」でも安全ではない。
resize の途中を読むと null や無限ループが起きる。
`DailyTotalCache.byPlayer` と `VarietyPenaltyEvaluator.ringBuffers` と `curveCache` を `ConcurrentHashMap` にする。

値そのものの可視性も要る。
`VarietyRingBuffer` は `record()` のたびに不変の `Snapshot` を `volatile` フィールドへ公開し、`VarietyPenaltyEvaluator#snapshot` はそのフィールドだけを読む。
`DailyTotalCache.DayEntry` の累計も同様に不変 snapshot として公開する。
これで読み手はロックを取らずに整合した値を見る。

書き手を 1 本に保つため、ワーカーへの境界キューはタスクの直和を運ぶ。

- **報酬タスク**：段階 4 から 11 を走らせる。
- **制御タスク**：cache の warmup 反映、`unload`、`reset` を走らせる。

`DailyTotalCache#warmup` と `VarietyPenaltyEvaluator#warmup` は現在、`AsyncExecutor` で JDBC を読んだあと `runOnMain` で cache へ書いている。
この書き込み先をワーカーへ変える。
JDBC の読み出しは従来どおり `AsyncExecutor` のプールで行い、ワーカーを I/O で止めない。

プレイヤー切断時の `unload` と `/jobs admin reset-daily-cap` の `reset` も制御タスクとして流す。
in-flight の報酬処理と競合しなくなる。

`SpecialtyService` の cache は既に `ConcurrentHashMap` なので変更しない。

### daily_cap の日付の起点

`DailyCapEvaluator#evaluate` は `DailyTotalCache#todayTotal` を呼び、そこで `Instant.now(clock)` から当日を求めている。
キューが深いと、23:59 に起きたアクションが日付をまたいだあとに処理され、翌日の枠へ計上される。

日付の起点を処理時刻から `ctx.occurredAt()` へ変える。
`DailyTotalView` の各メソッドに `LocalDate` を渡す形にし、キュー滞在時間に関わらず正しい枠へ計上する。

### 行動ログと async event

`ActionLogStage` は現在 `JobActionPaidEvent` を `asyncExecutor.runAsync` で発火しているが、ワーカー自体が main thread 外なので二重に投げる必要がなくなる。
ワーカー上で `callEvent` を直接呼ぶ形に単純化する。
async event の契約は変わらない。

段階 11 の enqueue もワーカー上で走るので、`action_log` の `occurred_at` 順序は保たれる。

### 拡張 API の契約変更

段階 7 と 8 はワーカー上で走るため、`JobRewardModifier#modify` と `JobRewardSplitter#split` は main thread では呼ばれなくなる。

- `spec/06-public-api.md` に「Jobs のワーカースレッドから呼ばれる。Bukkit API を叩く場合は実装側で main thread に戻す」を明記する。
- `JobRewardContext#getPlayer()` の javadoc に同じ警告を書く。
- `getPlayerUuid()` と `getPlayerName()` を `JobRewardContext` に足し、`getPlayer()` を使わずに済む道を用意する。

拡張実装が重い処理やブロッキング I/O を行うと、ワーカーが 1 本しかないため報酬処理全体が詰まる。
chain 1 件の所要時間が閾値を超えたら WARNING を出し、どの `getId()` が遅いかを特定できるようにする。

### config

```yaml
reward:
  decimals: 0
  rounding_mode: HALF_UP
  async:
    # 段階 4 から 11 を専用ワーカースレッドで実行するか。
    # false のとき全段階を main thread で同期実行する（従来の挙動）。
    enabled: true
    # ワーカーへの境界キュー容量。溢れた分は WARNING を出して捨てる。
    # 1 件あたり 200 バイト前後なので、100000 件で 20MB 程度を上限として見込む。
    queue_capacity: 100000
    # キュー深度がこの割合を超えたら WARNING を出す（30 秒に 1 回へ絞る）。
    backlog_warn_ratio: [0.5, 0.8]
    # 段階 10 の Vault 送金を main thread へ投げ返して実行するか。
    # Economy プラグインがスレッドセーフでない場合は true にする。
    economy_on_main: false
    # economy_on_main が true のとき、1 tick に main thread で処理する送金の上限。
    main_work_per_tick: 200
    # 拡張 Modifier / Splitter chain 1 件の所要時間がこれを超えたら WARNING を出す（ミリ秒）。
    slow_extension_threshold_ms: 50
    # onDisable でキューを drain する上限（ミリ秒）。
    drain_timeout_ms: 5000
```

`enabled: false` を kill switch として残す。
ブロック分割を切り替えるだけなので実装コストは小さく、本番で問題が出たときに設定だけで戻せる。

### 停止時の順序

`JobsServices#shutdown` にワーカーと `MainWorkQueue` の drain を挿む。

1. listener を unregister する。
2. `rewardWorker.drainAndStop(drain_timeout_ms)`。
3. `mainWorkQueue` を残り全件、その場でインラインに実行する。
4. `batchFlushWorker.drainAndStop`。
5. `asyncExecutor.shutdown`。
6. `dataSource.close`。

ワーカーが main thread の完了を待たないので、`onDisable` が main thread で drain を待ってもデッドロックしない。

disable 中の plugin へスケジュールしたタスクは実行されないため、`runTaskTimer` のドレイナは止まっている。
`onDisable` 自体が main thread で走るので、手順 3 で `MainWorkQueue` を直接空にすれば送金は正しいスレッドで完了する。
tick あたりの上限はここでは掛けない。停止処理なので滞在時間を気にする必要がない。

`economy_on_main: false` のときは送金がワーカースレッド上で完結するため、この経路を通らない。
手順 3 で残るのは rare の broadcast だけで、停止時に取りこぼしても差し支えない。

手順 2 がタイムアウトした場合、ワーカーは処理途中のまま止まる。
未処理件数を WARNING に出し、失われた報酬の規模が運用者に分かるようにする。

## 実装順

### Phase A: 土台

1. `RewardWorkQueue`（`LinkedBlockingQueue` ベースの境界キュー、水位警告と drop 計数つき）と `RewardWorker`（単一 daemon スレッド）を新規実装する。`ActionLogWriteQueue` と `BatchFlushWorker` の構成に倣う。
2. `MainWorkQueue` と `runTaskTimer` のドレイナ、および停止時のインライン drain を実装する。
3. `PluginConfig.RewardConfig` に `AsyncConfig` を足し、`config.yml` と `ConfigLoader` を更新する。

この時点では既存の挙動は変わらない。

### Phase B: context と状態

1. `PipelineContext` に `playerUuid`, `playerName`, bypass boolean 4 種, `detachBukkitRefs()` を足し、`Player` の強参照を捨てる。`zeroReasons` を遅延生成にする。
2. 各 Stage の `hasPermission` 呼び出しを boolean 参照へ置き換える。
3. `EconomyTransferStage` を `Bukkit.getOfflinePlayer(uuid)` 経由に変える。
4. `DailyTotalView` と `DailyTotalCache` と `DailyCapEvaluator` に `LocalDate` を渡す形へ変え、日付の起点を `occurredAt` にする。
5. `DailyTotalCache` と `VarietyPenaltyEvaluator` を `ConcurrentHashMap` 化し、snapshot を volatile 公開に変え、`warmup` と `unload` と `reset` を制御タスク経由にする。

### Phase C: driver

1. `Stage#affinity()` を足し、`RewardPipeline` を prologue と worker の 2 ブロックに分割する。
2. `AdvancementRevokeStage` を prologue の末尾へ移す。
3. `EconomyTransferStage` に `economy_on_main` を渡し、送金をワーカー上で行うか `MainWorkQueue` へ積むかを切り替える。
4. `RareRollStage` の broadcast を `runOnMain` へ投げる。

### Phase D: 契約と後片付け

1. `JobRewardContext` に `getPlayerUuid` と `getPlayerName` を足し、javadoc と `spec/06-public-api.md` を更新する。
2. 拡張 chain の所要時間計測と WARNING を足す。
3. `/jobs admin` にキュー深度と累計 drop 件数とワーカー処理レートの表示を足す。`KvsCommands` と同じ階層に置く。
4. `JobsServices#shutdown` の順序にワーカーと `MainWorkQueue` の drain を挿む。
5. `spec/04-reward-pipeline.md` と `plan/threading.md` を更新し、ADR-0021 を書く。

## テスト

新規に必要なもの。

- `RewardWorkerTest`：FIFO 保証、`queue_capacity` 超過での drop と WARNING、水位警告が 30 秒に 1 回へ絞られること、`drainAndStop` のタイムアウト、報酬タスクと制御タスクの順序。
- `MainWorkQueueTest`：1 tick の処理件数が `main_work_per_tick` を超えないこと、残りが次の tick へ繰り越されること。
- `DailyCapDateTest`：`occurredAt` が前日のタスクを日付をまたいだあとに処理しても、前日の枠へ計上されること。
- `RewardPipelineSplitTest`：affinity による 2 ブロック分割、prologue の HALT でワーカーへ積まれないこと、`enabled: false` で全段階が同期実行されること。
- `DailyCapWorkerTest`：同一プレイヤーの N 件をワーカー経由で流し、累計が正確で上限を超えないこと。
- `VarietyPenaltyWorkerTest`：同じ条件で比率と multiplier が同期実行時と一致すること。
- `PipelineContext` の `detachBukkitRefs` 後に `subject()` が empty を返すこと。

既存テストへの影響。

- `BaseRewardStageTest`, `BuiltinModifierStageTest`, `SpecialtyStageTest`, `AntiAutomationStageTest`, `RewardRoundingStageTest` は `PipelineContext` の構築方法が変わるため追従が必要。
- ワーカーを同期実行するテストダブルを用意し、境界キューを介する処理を決定的に検証できるようにする。

## 残る論点

ワーカーが 1 本なので、グローバルな head-of-line blocking が起きる。
ホッパー農場のようなイベント連射で 1 人がキューを埋めると、他のプレイヤーの報酬も遅れる。
容量超過時の drop は公平でなく、後から来た正当なアクションが捨てられる。
プレイヤーごとに公平さを保つ仕組みは、キュー深度の実測を見てから判断する。

恒常的な過負荷に対しては、容量ではなく 1 件あたりのワーカー処理時間を削るほうが効く。
支配的なコストは Vault 送金で、Economy プラグインが DB を叩く実装なら 1 件で数ミリ秒かかる。
同一プレイヤーの複数アクションを 1 回の送金にまとめれば処理レートは桁で変わるが、支払いの粒度と即時性が落ちる。
水位警告が実際に出るようになってから検討する。

`JobRewardContext#getPlayer()` を露出したまま「Bukkit API は自分で main に戻せ」と要求する形は、拡張実装が誤りやすい。
`getPlayer()` を deprecate して `getPlayerUuid()` へ寄せる案は、`Player` を必要とする正当な用途（インベントリ確認による装備ボーナスなど）を潰すため、今回は採らない。

`EventDispatcher` がマッチしなかったときに `revokeCriteria` を呼ばない既存の挙動は、advancement が awarded のまま固定されて二度と発火しない状態を生む。
本件とは独立した問題なので、別途扱う。

## 関連文書

- [threading.md](./threading.md)
- [class-structure.md](./class-structure.md)
- [spec/04-reward-pipeline.md](../spec/04-reward-pipeline.md)
- [spec/06-public-api.md](../spec/06-public-api.md)
