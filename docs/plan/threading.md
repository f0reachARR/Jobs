# スレッドモデルとライフサイクル

`spec/04-reward-pipeline.md` の「スレッドモデル」節と `spec/05-persistence.md` の「書き込み・読み込みのスレッドモデル」節、`spec/07-ui.md` の「Bedrock Edition 対応の注意点」を統合し、実装として満たすべき制約を 1 箇所にまとめる。

## スレッドの分類

Job プラグインが扱うスレッドは 4 種類。

- **Bukkit main thread**：全 Bukkit イベント、コマンド executor、`Player` などの状態変更、`AdvancementProgress#revokeCriteria`。
- **プラグイン所有の書き込みワーカ**（`persistence.async.BatchFlushWorker`、単一 daemon スレッド）：`action_log` と `daily_reward_total` のバッチ INSERT。
- **プラグイン所有の非同期実行プール**（`util.AsyncExecutor` が保持する `ExecutorService`）：`ActionLogQueryService` の読み込みクエリ、その他明示的 async 化。
- **BedrockDialog のコールバックスレッド**：Java Edition はメインスレッド保証寄り、Bedrock Edition はネットワークスレッドから呼ばれる可能性がある。

## パイプラインの実行スレッド

`pipeline.RewardPipeline` の段階 1〜10 は main thread で同期実行する。

- 段階 1（Matcher）：main thread。listener の中で走る。
- 段階 2（専業判定）：main thread。
- 段階 3（自動化対策）：main thread。`Entity#getEntitySpawnReason`, `PDC` 読み書き、KVS の `get` はいずれも main thread から。
- 段階 4〜7：main thread。
- 段階 8（Splitter）：main thread。ただし Splitter 実装内で Vault の同期送金を叩くため、実質的に Vault の契約に従う。
- 段階 9（丸め）：main thread。`BigDecimal#setScale` のみで I/O 無し。
- 段階 10（Economy 送金）：main thread。Vault は同期前提。
- 段階 11（行動ログ書き込み）：main thread から `ActionLogWriteQueue#enqueue` を呼ぶ。実 INSERT は `BatchFlushWorker` に載せる。`JobActionPaidEvent` の発火は async event としてこの直後に行う。
- 段階 12（`revokeCriteria`）：main thread。

Stage の interface は「main thread 前提」で書く。async に載せたい処理は Stage の中で `AsyncExecutor` に投げる、または Stage 自体を「async 化する Stage」として分けて書く（現時点では ActionLogStage のみ）。

## リポジトリ読み書きのスレッド

`ActionLogRepository`, `PlayerJobRepository`, `DailyRewardTotalRepository` の呼び出し規約は次のとおり。

- **書き込み**：`BatchFlushWorker` から `insertBatch`, `addBatch` を呼ぶ。バッチ間隔 1 秒 / バッチサイズ 1000 件。プラグイン停止時に `drain(timeout)` を呼ぶ。
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

`SpecialtyService#select` の内部で `PlayerJobRepository#insertSelection` を呼ぶが、これは main thread で JDBC を叩くと BLOCKS on network I/O になる。
専業選択は 1 プレイヤーあたり数回程度なので、main thread から同期で叩く方針。
負荷が問題になれば、insertSelection を async 化して結果を main thread に戻す形に切り替える余地を残す。

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
10. `AntiAutomationCoordinator`, `ExtensionModifierChain`, `SplitterChain`, `SpecialtyService`, `DialogService` を wire。
11. `ActionLogWriteQueue`, `BatchFlushWorker` を起動。
12. 全 listener を register。
13. `JOB_PLUGIN_READY` ライフサイクルイベントを発火（拡張プラグインが Modifier / Splitter を register する契機）。

### 停止時（`onDisable`）

順序が重要。

1. 全 listener を unregister（新規イベントを受け付けない）。
2. `ActionLogWriteQueue.drain(30s)`：キュー内エントリの INSERT を待つ。
3. `BatchFlushWorker` を join。
4. `AsyncExecutor` の shutdown、`awaitTermination`。
5. `MySqlDataSource` を close（HikariCP）。
6. `InMemoryKVStore` を破棄。
7. `LocaleRegistry`, `JobRegistry` の解放は GC 任せ。

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
- BedrockDialog callback の中で例外：`ui.DialogService` の `runOnMain` の内側で try-catch し、`Logger.warning` を出す（Dialog callback の failure はユーザ体験に直結するため）。

## タイムゾーンと日付

`daily_reward_total` の `reset_at`（config で `"00:00"` などを指定）はサーバのシステムタイムゾーンで解釈する（`ZoneId.systemDefault()`）。
`daily_reward_total.reward_date` は同じタイムゾーンで `LocalDate` に丸める。
日跨ぎ時にキャッシュを新しい日付にリセットする scheduler は Bukkit の `runTaskTimer` で「起動時 + 1 分」ごとに現在時刻を見て切り替える（分単位の遅延は許容範囲）。

## 関連文書

- [class-structure.md](./class-structure.md)
- [phases.md](./phases.md)
- [spec/04-reward-pipeline.md](../spec/04-reward-pipeline.md)
- [spec/05-persistence.md](../spec/05-persistence.md)
- [spec/07-ui.md](../spec/07-ui.md)
