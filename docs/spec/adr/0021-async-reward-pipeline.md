# ADR-0021 報酬パイプラインを単一ワーカーで非同期化する

## ステータス

受け入れ

## 背景

報酬パイプラインの段階 1 から 12 は、これまで listener の中で main thread で同期実行していた。
1 アクションあたりの処理は、ring buffer の集計と日次累計の read-modify-write、拡張 Modifier と Splitter の chain、`BigDecimal` の丸め、Vault への送金である。
このうち Vault 送金は Economy プラグインの実装次第で DB を叩き、1 件で数ミリ秒かかる。
採掘や自動化装置で毎秒数十件から数百件のアクションが起きる環境では、tick を圧迫する。

非同期化を考えるうえで、main thread から動かせない段階が 2 つある。

段階 3 の自動化対策は Bukkit の状態を直接読む。
`UnplantedCropCheck` と `RecentlyPlacedBreakCheck` は `Block#getBlockData` と `Block#getState` を経由して PDC を読み、`SpawnerOriginCheck` は `Entity#getEntitySpawnReason` を呼ぶ。
さらに `DetectionSubject` が生の `Block` と `Entity` 参照を持ち、これらは同 tick 内でしか安全に触れない。

段階 12 の `revokeCriteria` も main thread 専用である。

一方、非同期化を阻む可変状態が 2 つある。

- **`DailyTotalCache`**：当日累計の read-modify-write を行う。
- **`VarietyRingBuffer`**：直近 window 件のキー列を持ち、「今回のアクションを含めない直近 window 件」から比率を求める。

この 2 つは単にスレッドセーフでないだけでなく、同一プレイヤー内の実行順序に依存する。
複数スレッドで同じプレイヤーの 2 件が同時に走ると、比率が狂い、上限を超えて支払う。

並列度をどう取るかで設計が分かれる。
プレイヤー単位で実行を直列化すればプレイヤー間の並列を保てるが、末尾 future の連鎖とプレイヤーごとの未処理カウンタと map entry 撤去時の競合を自前で扱うことになる。
ワーカーを 1 本に絞れば、これらの機構がすべて不要になる。

## 決定

prologue（段階 1 から 3 と 12）を main thread で同期実行し、段階 4 から 11 を Jobs 専用の単一 daemon スレッドへ移す。

### 実行モデル

`Stage` に `Affinity{MAIN, WORKER}` を持たせ、`RewardPipeline` が wiring 時に宣言順のまま 2 ブロックへ分割する。
prologue は listener の呼び出しスレッドでそのまま実行し、HALT が返らなければ残りを境界キューへ積む。
prologue をスケジューラ越しにしないのは、`Block` と `Entity` 参照を同 tick 内で使い切る必要があるためである。

段階 12 を末尾から prologue の末尾へ移す。
ワーカー経由にすると advancement の再発火が遅れ、その間のイベントを取りこぼす。
`revokeCriteria` は報酬の算出結果に依存しないので、位置を移しても意味論は変わらない。
prologue で HALT する段階は 1 から 3 に限られるため、前倒ししても「どの場合に revoke が走るか」は従来と一致する。

ワーカーへ渡す前に `PipelineContext#detachBukkitRefs()` を呼び、`DetectionSubject` を空へ差し替える。
段階 4 以降が同 tick 前提の Bukkit 状態に触れないことをコードで保証する。

bypass 権限 4 種は prologue で解決して boolean で持つ。
`Player#hasPermission` をワーカーから呼ばずに済み、パイプライン全体で判定が一貫する。

`Player` の強参照は保持する。
同一プレイヤーの複数タスクが同じ 1 個を共有するのでキューが深くても取り分は増えず、スレッド安全性が保証されていない `Bukkit.getPlayer(UUID)` をワーカーから呼ばずに済む。
1 アクションごとに別物を掴むのは `DetectionSubject` のほうで、そちらは detach する。

### main thread への投げ返し

ワーカーは main thread の完了を待たない。
`Bukkit.getScheduler().runTask` は呼び出し側をブロックしないため、投げてから次のタスクへ進む。

rare の announce は `AsyncExecutor#runOnMain` へ直接投げる。
発生頻度が低いので受け皿を挟む必要がない。

`reward.async.economy_on_main` が true のときの送金は `MainWorkQueue` に積み、`runTaskTimer` のドレイナが毎 tick `main_work_per_tick` 件まで取り出す。
送金 1 件ごとに `runTask` を積むと、スケジュールしたタスクが次の tick でまとめて実行されるため、キューに数万件が溜まっている状態では 1 tick に数万件の送金が走る。

### 境界キュー

`LinkedBlockingQueue(capacity)` を使い、既定容量は 100,000 件とする。
`ArrayBlockingQueue` は容量分の配列を起動時に確保するため、100,000 件で 800 KB を常に占める。
1 件あたりの実メモリは 200 バイト前後で、満杯時に 20 MB 程度を見込む。

キューは報酬タスクと制御タスク（cache の warmup 反映、`unload`、`reset`）の直和を運ぶ。
制御タスクを優先実行しないのは、`unload` が先回りすると「キューに残っている同プレイヤーの報酬処理が cache を作り直す」事故が起きるためである。
制御タスクは捨てず、容量超過でも 100 ミリ秒待って入れようとする。

容量が吸収できるのはバーストだけである。
到着レートがワーカーのスループットを恒常的に上回る状態では、容量を増やしても drop が遅延に置き換わる。
深度が容量の 50% と 80% を超えたら WARNING を出し（30 秒に 1 回へ絞る）、`/jobs admin queue` から深度と累計 drop 件数と処理レートを読めるようにする。

溢れた報酬タスクは捨てる。
main thread をブロックすればサーバが止まり、main thread でインライン実行すれば可変状態の書き手が 2 本になって単一ワーカーの前提が崩れる。

### 可変状態

書き手はワーカー 1 本だが、読み手は main thread にも居る（`/jobs status`、Dialog UI、PlaceholderAPI）。
`HashMap` は「単一書き手 + 別スレッドからの読み」でも安全ではない。resize の途中を読むと null や無限ループになる。

- `DailyTotalCache.byPlayer` を `ConcurrentHashMap` にし、`DayEntry.total` を `volatile double`、`perJob` を `ConcurrentHashMap` にする。volatile double の読み書きは atomic なので、ロックも per-action の割り当ても要らない。
- `VarietyRingBuffer` は全メソッドを `synchronized` にする。内部が `ArrayDeque` と `HashMap` で、整合した観測値を返すには複数フィールドをまとめて読む必要がある。臨界区間は異なるキー数だけのループで、UI からの読み出しも稀なので競合はほぼ起きない。件数と比率と最多キーを 1 回のロックで返す `snapshot()` を足す。

`warmup` の JDBC 読み出しは従来どおり `AsyncExecutor` のプールで行い、cache への書き込みだけを制御タスクとして流す。
ワーカーを I/O で止めない。

### daily_cap の日付

計上先の日付を処理時刻から `occurredAt` 起点へ変える。
キューが深いと、23:59 のアクションが日付をまたいだあとに処理され、翌日の枠へ計上される。
`DailyTotalView` の各メソッドが `LocalDate` を受け取る形にした。

### 拡張 API

段階 7 と 8 はワーカー上で走るため、`JobRewardModifier#modify` と `JobRewardSplitter#split` は main thread では呼ばれなくなる。
`JobRewardContext` に `getPlayerUuid()` と `getPlayerName()` を足し、`getPlayer()` を使わずに済む道を用意する。
`getPlayer()` は残すが、ワーカーから触れてよいのは参照の同一性と状態を持たない読み出しに限ると javadoc に明記する。

ワーカーが 1 本なので、遅い拡張実装はサーバ全体の報酬処理を詰まらせる。
chain 1 件の所要時間が `reward.async.slow_extension_threshold_ms` を超えたら、どの `getId()` が遅いかを WARNING に出す。

### 停止時

`onDisable` の順序は、ドレイナ停止、ワーカー drain、`MainWorkQueue` のインライン実行、`BatchFlushWorker` drain、`AsyncExecutor` shutdown、`DataSource` close とする。

ワーカーの drain はワーカースレッド自身が行う。
呼び出し元でインライン実行すると可変状態の書き手が 2 本になる。
タイムアウトした場合は未処理件数を WARNING に出す。

`onDisable` は main thread で走るので、`MainWorkQueue` をそこで直接空にすれば送金は正しいスレッドで完了する。
disable 中の plugin へスケジュールしたタスクは実行されないが、この経路を通らないので影響しない。

### kill switch

`reward.async.enabled: false` で全段階を main thread で同期実行する形に戻せる。
ブロック分割を切り替えるだけなので実装コストは小さく、本番で問題が出たときに設定変更だけで戻せる。

## 結果

- 段階 4 から 11 が tick から外れる。`economy_on_main: false` のとき、Vault 送金も含めて main thread に残らない。
- 単一ワーカーなので、同一プレイヤーの直列性はグローバル FIFO の特別な場合として自動的に満たされる。プレイヤー単位で実行を直列化する仕組みも、プレイヤーごとの未処理件数を数える仕組みも要らない。
- 境界キューと単一 daemon という構成が `ActionLogWriteQueue` と `BatchFlushWorker` と同じ型になり、容量と drop 時の警告と `drain(timeout)` の意味論を流用できる。
- 報酬の支払いがアクションの数 tick 後になる。プレイヤーから見て即時ではない。
- ワーカーが 1 本なので、グローバルな head-of-line blocking が起きる。イベント連射で 1 人がキューを埋めると、他のプレイヤーの報酬も遅れる。容量超過時の drop は公平でなく、後から来た正当なアクションが捨てられる。
- `JobActionPaidEvent` は従来どおり async event だが、発火元がワーカースレッドになる。`enabled: false` のときだけ `AsyncExecutor` へ逃がす（async event は main thread から発火できない）。
- `DailyTotalView` の signature が変わるため、この interface を実装している箇所は `LocalDate` を受ける形へ追従する。

## 選択しなかった代替案

- **プレイヤー単位の直列レーンにする案**：`ConcurrentHashMap<UUID, CompletableFuture<Void>>` で末尾 future を連鎖させ、プレイヤー間の並列を保つ。head-of-line blocking を各プレイヤーに閉じ込められるが、プレイヤーごとの未処理カウンタと map entry 撤去時の競合と `CompletableFuture` chain の管理が増える。さらに main thread への hop をレーンに載せるため `runOnMainAsync` のような新 API が必要になり、`onDisable` で main thread がレーンの hop を待つデッドロックが生まれる。単一ワーカーはこれらをまとめて消す。並列度が要るかどうかはキュー深度の実測を見てから判断する。
- **送金のみ非同期化する案**：パイプライン構造を変えずに `deposit` だけプールへ投げる。実装量は最小だが、内蔵 Modifier の計算と丸めが main thread に残り、`DailyTotalCache` への書き込みが main thread と送金スレッドで分かれる。
- **溢れたときに main thread でインライン実行する案**：報酬を落とさずに済むが、可変状態の書き手が 2 本になる。ロックで守れば整合するが、main thread が report worker を待つ形になり tick を止める。
- **拡張 API を「既定 main thread、opt-in で async」にする案**：`isThreadSafe()` のような default メソッドを足し、既存実装の契約を壊さない。ただし段階 7 と 8 を main thread に戻す hop が入り、非同期化の利益が内蔵 Modifier と丸めに限られる。拡張プラグインは自前実装なので、契約変更を明記して追従するほうが素直である。

## 関連 ADR

- [ADR-0012 報酬パイプラインの拡張点を Modifier と Splitter で公開する](./0012-reward-modifier-extension.md)
- [ADR-0019 報酬額を小数値として扱う](./0019-decimal-reward.md)
