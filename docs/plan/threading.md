# スレッドモデルとライフサイクル

`spec/04-reward-pipeline.md` の「スレッドモデル」節と `spec/05-persistence.md` の「書き込み・読み込みのスレッドモデル」節、`spec/07-ui.md` の「Bedrock Edition 対応の注意点」を統合し、実装として満たすべき制約を 1 箇所にまとめる。

## スレッドの分類

Job プラグインが扱うスレッドは 5 種類。

- **Bukkit main thread**：全 Bukkit イベント、コマンド executor、`Player` などの状態変更、`AdvancementProgress#revokeCriteria`。
- **プラグイン所有の報酬ワーカ**（`pipeline.async.RewardWorker`、単一 daemon スレッド）：報酬パイプラインの段階 4 から 11。
- **プラグイン所有の書き込みワーカ**（`persistence.async.BatchFlushWorker`、単一 daemon スレッド）：`action_log` と `daily_reward_total` のバッチ INSERT。
- **プラグイン所有の非同期実行プール**（`util.AsyncExecutor` が保持する `ExecutorService`）：`ActionLogQueryService` の読み込みクエリ、cache warmup の JDBC 読み出し、その他明示的 async 化。
- **BedrockDialog のコールバックスレッド**：Java Edition はメインスレッド保証寄り、Bedrock Edition はネットワークスレッドから呼ばれる可能性がある。

## パイプラインの実行スレッド

`pipeline.Stage` の `affinity()` が実行スレッドを宣言し、`pipeline.RewardPipeline` が wiring 時に prologue（`MAIN`）と worker（`WORKER`）の 2 ブロックへ分割する。
詳細は [async-reward-pipeline.md](./async-reward-pipeline.md) と [ADR-0021](../spec/adr/0021-async-reward-pipeline.md) を参照。

prologue は listener の呼び出しスレッド（main thread）でそのまま実行する。
スケジューラを介さないのは、`Block` と `Entity` 参照を同 tick 内で使い切る必要があるためである。

- 段階 1（Matcher）：main thread。listener の中で走る。
- 段階 2（専業判定）：main thread。
- 段階 3（自動化対策）：main thread。`Entity#getEntitySpawnReason`, `PDC` 読み書き、KVS の `get` はいずれも main thread から。
- 段階 12（`revokeCriteria`）：main thread。ワーカー経由にすると advancement の再発火が遅れるため、末尾ではなく prologue の末尾に置く。

worker ブロックは `RewardWorker` の単一 daemon スレッドで走る。
prologue が HALT を返さなければ、`RewardWorkQueue` へ積んで listener は即座に戻る。

- 段階 4（基礎報酬）〜段階 9（丸め）：ワーカー。Bukkit API を直接叩かない。
- 段階 5（rare）の announce だけ `AsyncExecutor#runOnMain` へ投げ返す。
- 段階 10（Economy 送金）：ワーカー。`reward.async.economy_on_main` が true のときは `MainWorkQueue` に積み、`runTaskTimer` のドレイナが毎 tick `main_work_per_tick` 件まで処理する。
- 段階 11（行動ログ書き込み）：ワーカーから `ActionLogWriteQueue#offer` を呼ぶ。実 INSERT は `BatchFlushWorker` に載せる。`JobActionPaidEvent` はワーカー上で直接発火する。

ワーカーは main thread の完了を待たない。
`Bukkit.getScheduler().runTask` は呼び出し側をブロックしないので、投げてから次のタスクへ進む。

`reward.async.enabled: false` のときは分割せず、全段階を main thread で同期実行する。
このとき `JobActionPaidEvent` は `AsyncExecutor` へ逃がす（async event は main thread から発火できない）。

## 報酬ワーカと可変状態

ワーカーを 1 本に絞ることで、段階 4 から 11 が触る可変状態の書き手が 1 スレッドだけになる。
`modifier.variety.VarietyRingBuffer` の比率計算と `modifier.dailycap.DailyTotalCache` の累計 read-modify-write は、これで直列化される。

読み手は main thread にも居る（`/jobs status`、Dialog UI、PlaceholderAPI）。
`HashMap` は単一書き手でも別スレッドからの読みで壊れるため、次の形で守る。

- **`DailyTotalCache`**：`ConcurrentHashMap` + `volatile double`。ロックも per-action の割り当ても要らない。
- **`VarietyRingBuffer`**：全メソッド `synchronized`。整合した観測値を返すには複数フィールドをまとめて読む必要がある。

cache の warmup 反映と `unload` と `reset` は `pipeline.async.RewardDispatcher#dispatchControl` 経由で同じキューへ流す。
報酬タスクと同じ順序で処理されるので、`unload` が先回りして「キューに残っている同プレイヤーの報酬処理が cache を作り直す」事故が起きない。

warmup の JDBC 読み出しは `AsyncExecutor` のプールで行い、ワーカーを I/O で止めない。

## リポジトリ読み書きのスレッド

`ActionLogRepository`, `PlayerJobRepository`, `PlayerJobHistoryRepository`, `DailyRewardTotalRepository` の呼び出し規約は次のとおり。

- **書き込み**：`BatchFlushWorker` から `insertBatch`, `addBatch` を呼ぶ。バッチ間隔 1 秒 / バッチサイズ 1000 件。プラグイン停止時に `drain(timeout)` を呼ぶ。`ActionLogWriteQueue#offer` の呼び出し元は報酬ワーカである。
- **読み込み**：`ActionLogQueryService` の各メソッドは `CompletableFuture` を返す形で外部プラグインに露出する。実際の JDBC 呼び出しは `AsyncExecutor` のプール上で走る。Bukkit main thread からの同期呼び出しは想定しない。
- **プラグイン内部での読み込み**（`VarietyRingBuffer` のログイン時初期化、`DailyTotalCache` の初期化）：`PlayerJoinEvent` を main thread で受けたら、そこから `AsyncExecutor.supplyAsync(...)` に投げて結果を main thread に戻して cache に格納する。ログイン直後の 1 秒未満はキャッシュが空扱いになる（過去 window 件は 0 として扱う）許容範囲とする。

## KVS のスレッド

`kvs.memory.InMemoryKVStore` は Caffeine ベースで、`Cache` は thread-safe。
main thread からの `get/put/remove` を前提とするが、`OperatorTracker` の `InventoryMoveItemEvent` は hopper tick と同時に走るため頻度が高い。
Caffeine の `expireAfterWrite` に任せ、明示 `remove` は基本呼ばない。

## BedrockDialog コールバック

`ui.DialogService` は BedrockDialog callback を受け取ったら、Bukkit API を叩く前に必ず main thread に戻す（[ADR-0014](../spec/adr/0014-bedrock-dialog.md)）。

```java
UnifiedDialog dialog = MultiButtonDialog.builder()
    .button(label, player -> util.AsyncExecutor.runOnMain(() -> {
        specialtyService.select(player.getUniqueId(), jobId);
    }))
    .build();
```

`AsyncExecutor#runOnMain(Runnable)` は `Bukkit.getScheduler().runTask(plugin, task)` の wrapper。

`SpecialtyService#select` / `change` / `setForced` の内部で `PlayerJobRepository#upsert` と `PlayerJobHistoryRepository#append` を続けて呼ぶが、いずれも main thread で JDBC を叩くと BLOCKS on network I/O になる。
専業選択・変更は 1 プレイヤーあたり数回程度なので、main thread から同期で叩く方針。
負荷が問題になれば、これらを async 化して結果を main thread に戻す形に切り替える余地を残す。

## ライフサイクル

### 起動時（`onEnable`）

順序が重要。

1. `PluginConfig` を読む。
2. `LocaleRegistry` を初期化（`ja_jp` 必須）。
3. `MySqlDataSource` を起動、`SchemaInitializer` で DDL 実行、ヘルスチェック。
4. `InMemoryKVStore` を初期化。
5. リポジトリ 3 種を wire。
6. `JobYamlLoader` で `plugins/Jobs/jobs/*.yml` を読み、`JobRegistry` に格納。
7. `TagResolver` を `LifecycleEvents.SERVER_LOAD` フックで初期化（サーバ起動完了後にタグが揃うため）。
8. `ShadowDetector` を走らせて警告出力。
9. `VaultEconomyAdapter` を起動、Vault が無ければ致命エラー。
10. `SpecialtyService`, `DialogService` を wire。
11. `RewardWorkQueue`, `RewardWorker`, `MainWorkQueue` を起動。内蔵 Modifier より前に置く（`DailyTotalCache` と `VarietyPenaltyEvaluator` が `RewardDispatcher` を要求する）。
12. `VarietyPenaltyEvaluator`, `DailyTotalCache`, `DailyCapEvaluator` を wire。
13. `AntiAutomationCoordinator`, `ExtensionModifierChain`, `SplitterChain` を wire。
14. `ActionLogWriteQueue`, `BatchFlushWorker` を起動。
15. `RewardPipeline` を組む。`economy_on_main` が true なら `MainWorkQueue` の tick ドレイナを登録。
16. `FurnaceLedgerStore`, `FurnaceInputWatcher`, `SmeltRewardCollector` を wire し、collector の flush タイマを登録（`EventDispatcher` を要求するのでパイプラインの後）。
17. 全 listener を register。
18. `JOB_PLUGIN_READY` ライフサイクルイベントを発火（拡張プラグインが Modifier / Splitter を register する契機）。

### 停止時（`onDisable`）

順序が重要。

1. 全 listener を unregister（新規イベントを受け付けない）。
2. `SmeltRewardCollector.stop()`：まとめ待ちの精錬ぶんを flush する。報酬ワーカーの drain より先に流し込まないと落ちる（[ADR-0024](../spec/adr/0024-smelt-ledger-on-block-pdc.md)）。
3. `MainWorkQueue` の tick ドレイナを cancel。
4. `RewardWorker.drainAndStop(drain_timeout_ms)`：段階 4 以降の未処理分を走り切らせる。drain はワーカースレッド自身が行う（呼び出し元でインライン実行すると可変状態の書き手が 2 本になる）。タイムアウトしたら未処理件数を WARNING に出す。
5. `MainWorkQueue.drainAllInline()`：`onDisable` は main thread なので、ドレイナが止まっていてもここで直接空にすれば送金は正しいスレッドで完了する。
6. `ActionLogWriteQueue.drain(30s)`：キュー内エントリの INSERT を待つ。段階 11 の enqueue は 4 で終わっているので、この順序でログが落ちない。
7. `BatchFlushWorker` を join。
8. `AsyncExecutor` の shutdown、`awaitTermination`。
9. `MySqlDataSource` を close（HikariCP）。
10. `InMemoryKVStore` を破棄。
11. `LocaleRegistry`, `JobRegistry` の解放は GC 任せ。

### /jobs reload

`ReloadSub` が呼ばれると次を実行する。すべて main thread で。

1. `LocaleRegistry` を再ロード。
2. `JobRegistry` を再ロード（YAML 再パース）。
3. `TagResolver` を再 resolve。
4. `ShadowDetector` を再走行。
5. `AdvancementDatapackInstaller` の再インストール。
6. 起動時の `JOB_PLUGIN_READY` は再発火しない（拡張点の register/unregister は各プラグインの責務）。

reload 中のパイプライン実行を止めるため、`JobRegistry#swap(newState)` は `AtomicReference` の CAS で行い、進行中のパイプラインは旧 state を持ち回す（Stage 側で `ctx.jobDefinition` を保持しておく）。

## 例外ハンドリング

- Listener の中で例外が起きた場合：Bukkit の event bus が catch する。プラグイン側で catch する必要はないが、pipeline 呼び出しは try-catch で包み Listener 全体を落とさない。
- Stage の中で例外が起きた場合：`RewardPipeline` は各 Stage を try-catch する。段階に応じた振る舞い（[spec/04-reward-pipeline.md](../spec/04-reward-pipeline.md) の「エラーハンドリング」節）を守る。
- BatchFlushWorker の中で例外：INSERT 失敗は `Logger.severe`、リトライ。連続 5 回失敗でキューへの enqueue にバックプレッシャを掛ける（`enqueue` は失敗を返し、`ActionLogStage` は「ログ落とし」として `Logger.warning` を残す）。
- RewardWorker のタスクの中で例外：`Logger.severe` を出してワーカーは次のタスクへ進む。1 件の失敗でワーカーを落とさない。
- RewardWorkQueue の容量超過：報酬タスクは捨て、drop 件数をまとめて 30 秒に 1 回 WARNING に出す。制御タスクは捨てず 100 ミリ秒待って入れようとし、それでも入らなければ `Logger.severe`。
- BedrockDialog callback の中で例外：`ui.DialogService` の `runOnMain` の内側で try-catch し、`Logger.warning` を出す（Dialog callback の failure はユーザ体験に直結するため）。

## タイムゾーンと日付

`daily_reward_total` の `reset_at`（config で `"00:00"` などを指定）はサーバのシステムタイムゾーンで解釈する（`ZoneId.systemDefault()`）。
`daily_reward_total.reward_date` は同じタイムゾーンで `LocalDate` に丸める。

`daily_cap` の計上先の日付は、処理時刻ではなくアクションの発生時刻（`PipelineContext#occurredAt`）から求める。
報酬ワーカのキューが深いと、23:59 のアクションが日付をまたいだあとに処理され、翌日の枠へ計上されてしまう。
日跨ぎ時にキャッシュを新しい日付にリセットする scheduler は Bukkit の `runTaskTimer` で「起動時 + 1 分」ごとに現在時刻を見て切り替える（分単位の遅延は許容範囲）。

## 関連文書

- [class-structure.md](./class-structure.md)
- [phases.md](./phases.md)
- [spec/04-reward-pipeline.md](../spec/04-reward-pipeline.md)
- [spec/05-persistence.md](../spec/05-persistence.md)
- [spec/07-ui.md](../spec/07-ui.md)
