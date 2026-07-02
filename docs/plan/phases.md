# 実装フェーズ

`class-structure.md` の構成を、実装順序に落とし込む。
各フェーズは「終わったら手で動作確認して、次に進める粒度」を意識して切る。

## Phase 0: 骨組み

まず「プラグインとして起動して、`/jobs` コマンドで空 Dialog が出る」ところまで。

- `JobsPlugin` (JavaPlugin 継承、`onEnable` / `onDisable` のみ)
- `JobsBootstrap` (PluginBootstrap、`LifecycleEvents.COMMANDS` に空実装を登録)
- `JobsServices` の骨（`wire()` を空実装）
- `paper-plugin.yml` に `bootstrapper` を追記
- `util.MiniMessages`
- `util.AsyncExecutor`
- `command.JobsCommands`（`/jobs status` だけ、"not implemented" 応答）

**動作確認**：Paper サーバに導入して `/jobs status` を叩き応答が返る。

## Phase 1: 設定と i18n

YAML と多言語対応の土台。

- `config.PluginConfig`, `ConfigLoader`
- `i18n.LocaleRegistry`, `I18n`, `MissingKeyReporter`
- `resources/lang/ja_jp.yml`, `resources/lang/en_us.yml`（最低限のキー）
- `command.JobsCommands` を i18n 対応（`/jobs status` の文言を lang から取る）

**動作確認**：`plugins/Jobs/lang/ja_jp.yml` を編集して `/jobs reload` 相当（この段階では再起動で可）で反映される。

## Phase 2: ドメイン型と YAML パーサ

`jobs/*.yml` を読んで `JobDefinition` を作れるようにする。パイプラインはまだ動かない。

- `domain.job.*`（`JobId`, `JobDefinition`, `RewardEntry`, `RewardAmount`, `RareBonus`, `ActionType`, `ActionKey`, `MatchCriteria` の sealed hierarchy, `VarietyPenaltyConfig`, `AntiAutomationConfig`）
- `yaml.JobYamlLoader`, `MatchCriteriaParser`, `RewardAmountParser`, `VarietyPenaltyParser`, `AntiAutomationParser`
- `registry.JobRegistry`, `TagResolver`, `ActionKeyDeriver`, `ShadowDetector`

**動作確認**：`plugins/Jobs/jobs/combat.yml` を置いてサーバ起動時に "5 rewards loaded" のようなログが出る。
shadow 検出の警告テスト。
tag resolve が動くことを `#minecraft:undead` で確認。

## Phase 3: KVS と永続化の土台

DB 接続と KVS が上がる。書き込みはまだしない。

- `kvs.JobsKVStore`, `KvsKeys`, `memory.InMemoryKVStore`
- `persistence.dto.*`（`ActionLogRow`, `PlayerJobRow`, `ActionFilter`, `TimeRange`, `DailyRewardDelta`）
- `persistence.PlayerJobRepository`, `ActionLogRepository`, `DailyRewardTotalRepository`（interface）
- `persistence.mysql.MySqlDataSource`, `SchemaInitializer`（DDL 発行）
- `persistence.mysql.MySqlPlayerJobRepository`, `MySqlActionLogRepository`, `MySqlDailyRewardTotalRepository`
- `resources/sql/mysql/schema.sql`
- `util.UuidBytes`

**動作確認**：MySQL 8 コンテナを立て、起動時に `player_job`, `action_log`, `daily_reward_total` が作られる。
`SELECT 1` ヘルスチェックが通る。

## Phase 4: 専業選択と Dialog

DB に書き込む最初の経路。

- `specialty.SpecialtyService`, `CooldownPolicy`, `SpecialtyChangeResult`
- `ui.DialogService`
- `ui.SpecialtySelectDialog`, `SpecialtyChangeDialog`, `StatusDialog`（一部は Phase 6 で完成）
- `api.event.JobSpecialtyChangedEvent`（外部プラグイン向け）
- `command.SelectSub`, `ChangeSub`（`ReloadSub`, `StatusSub` は後）

**動作確認**：初回参加時に選択 Dialog が出る、選択で `player_job` に行が入る、`/jobs change` でクールダウン挙動が動く。
Bedrock クライアント（Geyser）でも同じ動線が通る（可能なら Floodgate を入れた環境で）。

## Phase 5: 検知とパイプラインの最小構成

「専業のプレイヤーが該当アクションをすると報酬が入り、`action_log` に書かれる」まで。
自動化対策と拡張点はまだ入れない。

- `detection.native_.*` の全 listener（15 個）
- `detection.EventDispatcher`, `DetectedAction`
- `matcher.RewardMatcher`, `MatchContext`
- `pipeline.RewardPipeline`, `PipelineContext`
- `pipeline.stage.MatcherStage`, `SpecialtyStage`, `BaseRewardStage`, `RareRollStage`, `EconomyTransferStage`, `ActionLogStage`
- `economy.VaultEconomyAdapter`
- `persistence.async.ActionLogWriteQueue`, `BatchFlushWorker`
- `api.event.JobActionPaidEvent`（Stage 11 の async event 発火）

**動作確認**：
- combat ジョブを選んだ状態でゾンビ討伐 → 報酬が入る、`action_log` に 1 行入る。
- combat 選択で `block_broken` イベントが発火 → 何も起こらない（マッチしないため）。
- combat 未選択で `entity_killed` が発火 → 何も起こらない（[ADR-0002](../spec/adr/0002-non-specialty-actions-discarded.md)）。
- `rare.chance` を 1.0 にして rare の発火とアナウンスを確認。
- shift-click クラフトで amount 掛け算が効いていることを確認。

## Phase 6: 内蔵 Modifier

variety_penalty と daily_cap を入れる。

- `modifier.variety.VarietyPenaltyEvaluator`, `VarietyRingBuffer`, `VarietyCurveLookup`
- `modifier.dailycap.DailyCapEvaluator`, `DailyTotalCache`
- `pipeline.stage.BuiltinModifierStage`
- `ActionLogRepository#recentKeys(player, jobId, limit)` を追加

**動作確認**：
- 同じキーだけを連打 → curve の閾値で multiplier が下がる。
- 日次キャップに達するまで discount 0、達したら 0。
- `/jobs status` の Dialog に単調性ペナルティが `disclosed_message` として出る。

## Phase 7: 自動化対策

6 種類すべて。

- `antiautomation.AntiAutomationCoordinator`
- `SpawnerOriginCheck`（追跡データ不要）
- `UnplantedCropCheck`, `PlantedFlagWriter`
- `RecentlyPlacedBreakCheck`, `PlacementRecorder`
- `AutoFedProcessingCheck`, `OperatorTracker`, `ContainerKind`
- `VillagerRepeatTradeCheck`, `TradeRecorder`
- `BreedNonPlayerBreederCheck`
- `pipeline.stage.AntiAutomationStage`
- `detection.tnt.TntPrimerTracker`（`via_tnt` フラグと同時に必要）

**動作確認**：
- スポナー MOB を倒す → 報酬 0、`action_log` に 0 で記録。
- 自分で植えていない小麦を刈る → 0。
- 石を置いてすぐ壊す → 0（`window_sec` 内）。
- Furnace にホッパーで自動投入した鉄を取り出す → 0。手で投入した鉄を取り出す → 通常報酬。
- 同 villager の同 recipe を連発 → 2 回目以降 0。
- Villager が交配した cow → 0。

## Phase 8: 拡張点

外部プラグインとの結節点。

- `api.extension.JobRewardModifier`, `JobRewardContext`, `ModifiedReward`, `JobRewardSplitter`, `JobRewardSplitContext`, `Split`, `Transfer`
- `api.lifecycle.JobsLifecycleEvents`（`JOB_PLUGIN_READY`）
- `api.JobsApi`（`getModifierRegistry`, `getSplitterRegistry`, `getQueryService` のアクセッサ）
- `modifier.ExtensionModifierChain`
- `splitter.SplitterChain`
- `pipeline.stage.ExtensionModifierStage`, `SplitterStage`
- `api.query.ActionLogQueryService` の実装（`persistence` 側）

**動作確認**：ダミーの Modifier / Splitter プラグインを別モジュールで書き、
`JOB_PLUGIN_READY` で登録できることと、パイプラインの `final_reward` / `net_paid` が期待どおりになることを確認する。
Splitter が例外を投げたら 1 件だけ skip されること。

## Phase 9: Track B（advancement）

- `detection.advancement.AdvancementListener`
- `detection.advancement.AdvancementDatapackInstaller`
- `pipeline.stage.AdvancementRevokeStage`
- 同梱データパック `data/jobs/advancement/` の雛形（1 例だけ入れる）

**動作確認**：`jobs:combat/charged_creeper_sword_kill` を発火させ、パイプラインが走り `revokeCriteria` で再度発火できるようになる。

## Phase 10: 運用 UI と reload

- `command.ReloadSub`（YAML / lang / tag / advancement を再読込）
- `/jobs status` の残り仕上げ（単調性、daily_cap 進捗バー）
- `Player#updateCommands()` の呼び出し（permission が動的に変わる箇所があれば）

**動作確認**：`/jobs reload` で YAML 変更が反映される。

## テストのフェーズ配分

- Phase 2, 6, 7 は特に unit test を厚めに書く（parser、curve lookup、KVS 判定）。
- Phase 5, 8 は MockBukkit を使ったフルスタックの integration test を書く（listener → pipeline → repository）。
- Phase 3 の MySQL 実装は、実 MySQL コンテナ相手の CI job を持たせるか手動確認に留めるか、運用判断で決める。

## 依存関係と着手順序の可視化

Phase 番号は「絶対に前フェーズが完了していないと動かない」場合のみ順序制約。
それ以外は並行可。

```
Phase 0 (骨組み)
  ↓
Phase 1 (config + i18n) ─┐
                          ├→ Phase 4 (Specialty + Dialog)
Phase 2 (Domain + YAML) ─┤
                          ├→ Phase 5 (Pipeline 最小構成)
Phase 3 (Persistence)  ──┘
                              ↓
                          Phase 6 (Modifier)
                              ↓
                          Phase 7 (AntiAutomation)
                              ↓
                          Phase 8 (Extension points)
                              ↓
                          Phase 9 (Advancement)
                              ↓
                          Phase 10 (Reload UI)
```

## Phase 完了の判定基準

各 Phase の「動作確認」節を手で通せることを完了とする。
CI で自動化するのは Phase 5 以降で unit test と MockBukkit test に限る（MySQL / Bedrock 起因のテストは手動確認に留める）。

## 関連文書

- [class-structure.md](./class-structure.md)
- [threading.md](./threading.md)
