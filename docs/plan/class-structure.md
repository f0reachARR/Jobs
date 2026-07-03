# クラス構成

`spec/` 全体を通して読める設計を、パッケージとクラス単位に落とし込む。
以下すべてのパッケージは `me.f0reach.jobs` を root とする。

## 全体方針

- **1 クラス 1 責務**を基本にする。パイプラインの各段階、自動化対策の 6 種類、Dialog 3 種類はそれぞれ個別クラスに分ける。
  実装を読むときに `spec/04-reward-pipeline.md` の段階番号と 1 対 1 で対応させたい。
- **公開 API と内部実装を分離する**。他プラグインが import するのは `me.f0reach.jobs.api.*` のみとし、それ以外の内部パッケージは他プラグインから触らない前提で設計する。
  `api/` 配下は「イベント型、拡張点 interface、クエリサービス interface、ライフサイクル event type」に限定する。
- **YAML 起源のドメイン型はイミュータブル**にする（`JobDefinition`、`RewardEntry`、`MatchCriteria` など）。
  リロードで丸ごと差し替える。可変状態は `registry/` の登録簿と、`pipeline/PipelineContext` などランタイム側に閉じる。
- **リポジトリインタフェースで永続化を切る**（[ADR-0018](../spec/adr/0018-repository-interface.md)）。
  具象実装は `persistence/mysql/` に、DTO 型は `persistence/dto/` に置く。呼び出し側は方言を知らない。
- **KVS 抽象を 1 枚 interface で切る**（[ADR-0015](../spec/adr/0015-kvs-abstraction.md)）。
  `kvs/JobsKVStore` を通して自動化対策の各追跡クラスがアクセスする。
- **Bukkit イベントリスナは薄く保つ**。listener は `DetectedAction` を組み立てるだけで、パイプライン本体は listener の外（`pipeline/RewardPipeline`）に置く。listener の単体テストが書きづらい制約と、Track A / Track B 合流点の共通化を両立させる。

## パッケージ一覧

```
me.f0reach.jobs
├── JobsPlugin              プラグインメインクラス (JavaPlugin)
├── JobsBootstrap           PluginBootstrap (paper-plugin.yml から呼ばれる)
├── JobsServices            起動時に組み立てた全 service の参照を持つ facade
│
├── api                     他プラグインが依存する公開 API
│   ├── event               JobActionPaidEvent, JobSpecialtyChangedEvent
│   ├── extension           JobRewardModifier, JobRewardSplitter, その Context/結果型
│   ├── query               ActionLogQueryService, ActionFilter, TimeRange
│   ├── lifecycle           JobsLifecycleEvents (JOB_PLUGIN_READY LifecycleEventType)
│   └── JobsApi             getModifierRegistry(), getSplitterRegistry(), getQueryService()
│
├── config                  config.yml のロードと POJO
├── domain                  YAML 起源のイミュータブルドメイン型
│   ├── job                 JobDefinition, RewardEntry, MatchCriteria, ...
│   ├── player              PlayerJob (UUID + jobId + selectedAt)
│   └── log                 ActionLogRow, DailyRewardDelta (書き込み用 DTO)
├── yaml                    ConfigurationSection → domain の parser 群
├── registry                グローバル状態を持つ登録簿 (reload で差し替え)
├── detection               Bukkit イベントと advancement の受け口
│   ├── native_             Track A の listener 群
│   ├── advancement         Track B の listener と data pack 設置
│   └── tnt                 TNT 起爆者追跡
├── matcher                 rewards[] 走査 (first match wins)
├── pipeline                報酬パイプライン (段階 1〜12)
│   └── stage               各段階の Stage クラス
├── antiautomation          6 種類の自動化対策と補助 tracker
├── modifier                内蔵 Modifier (variety_penalty, daily_cap) と拡張点 chain
│   ├── variety
│   └── dailycap
├── splitter                Splitter chain
├── economy                 Vault Economy Adapter
├── specialty               専業選択・変更・クールダウン
├── persistence             リポジトリ interface と具象実装
│   ├── dto
│   ├── async               action_log の非同期バッチ書き込み
│   └── mysql               HikariCP と MySQL 実装
├── kvs                     KVS interface と in-memory 実装
│   └── memory
├── ui                      Dialog の構築と表示
├── i18n                    lang/*.yml のロードと Locale 解決
├── command                 /jobs ルートコマンドとサブコマンド
└── util                    UUID <-> BINARY(16), MiniMessage, スレッドヘルパ
```

## 各パッケージの責務

### root

**JobsPlugin**：`onEnable` で `JobsServices` を組み立て、`LifecycleEventManager` にコマンドと data pack reload hook を登録する。
`onDisable` で `JobsServices.shutdown()` を呼び、非同期キューの flush と HikariCP の close を待つ。
BedrockDialog は `paper-plugin.yml` で `required: true` にしているため `BedrockDialog.init` は不要（[dependency/bedrock-dialog.md](../dependency/bedrock-dialog.md)）。

**JobsBootstrap**：`PluginBootstrap` として `LifecycleEvents.COMMANDS` にコマンド登録を紐付ける。
`JobsPlugin` インスタンスは持たないため、ここでは `Commands.literal("jobs")` のツリー構築のみ行い、実行 handler は「起動後に `JobsPlugin` が差し込む executor」を経由する。

**JobsServices**：Facade。以下を保持する。
- `JobRegistry`, `TagResolver`, `LocaleRegistry`, `JobsKVStore`, リポジトリ 3 種、`PipelineExecutor`, `ExtensionModifierChain`, `SplitterChain`, `SpecialtyService`, `DialogService`, `AsyncExecutor`, `VaultEconomyAdapter`。
- 起動時に全部を new する `wire()` と `shutdown()` を持つ。

### config

**ConfigLoader**：`plugin.getConfig()` から `PluginConfig` を組む。
不正値は `ConfigException` を投げ、`onEnable` は起動失敗にする。

**PluginConfig**：`SpecialtyModeConfig`, `DailyCapConfig`, `PersistenceConfig`, `KvsConfig` を子に持つ record。

### domain

イミュータブル・値クラス群。record 中心。

- `job.JobId`：ASCII 小文字とアンダースコアの正規化とバリデーション。
- `job.JobDefinition`：`id`, `displayName`, `icon`, `rewards`, `varietyPenalty`, `antiAutomation`。
- `job.RewardEntry`：`actionType`, `criteria`, `rewardAmount`, `rareBonus?`。
- `job.RewardAmount`：`sealed` (`Fixed(int)`, `Range(int min, int max)`)。
- `job.RareBonus`：`chance`, `rewardAmount`, `announceMessage`。
- `job.MatchCriteria`：`sealed` interface。子は `ActionType` に対応する 15 種類の record（`EntityKilled`, `BlockBroken`, `BlockPlaced`, ...）。
  各 record は「値、リスト、タグ」の指定を統一するために `EntityMatcher` / `MaterialMatcher` / `EnchantmentMatcher` などの小さな value type を経由して持つ。
- `job.ActionType`：enum。`ENTITY_KILLED`, `BLOCK_BROKEN`, ...。派生キーのプレフィクス（`kill:`, `break:` 等）をここで持つ。
- `job.ActionKey`：派生キー文字列の value type。生成規則は `registry.ActionKeyDeriver` に閉じる。
- `job.VarietyPenaltyConfig`：`enabled`, `window`, curve (`List<CurvePoint>`), `disclosedMessage`, `hideNumbers`。
- `job.AntiAutomationConfig`：各フラグ (`SpawnerOriginKills` enum、`RecentlyPlacedBreak` record、...)。
- `player.PlayerJob`：`playerUuid`, `jobId`, `selectedAt`。
- `log.ActionLogRow`：`action_log` の 1 行に対応。
- `log.DailyRewardDelta`：`daily_reward_total` の 1 バッチ更新に対応。

### yaml

**JobYamlLoader**：`plugins/Jobs/jobs/*.yml` を走査して `JobDefinition` に落とす。
Bukkit の `YamlConfiguration` を使い、`ConfigurationSection` から各フィールドを取り出す。
不正値は `YamlErrors` に集めて起動時にログ出力し、当該 job だけスキップする。

**MatchCriteriaParser**：`on` フィールドから `ActionType` を決め、対応する match フィールド (`entity`, `block`, `crop_mature`, ...) を parse する。
`entity: [zombie, husk]` のリスト形式、`entity: "#minecraft:undead"` のタグ形式、`entity: zombie` の単一形式を統一的に扱う。

**RewardAmountParser**：`reward: 5`、`reward: 0.5`、`reward: { min: 1.5, max: 3.5 }` を吸収する（[ADR-0019](../spec/adr/0019-decimal-reward.md)）。値は `double` で保持し、丸めはパイプライン末尾の丸めステージが行う。

**VarietyPenaltyParser / AntiAutomationParser**：spec 02 のとおり。

### registry

**JobRegistry**：`Map<JobId, JobDefinition>`。reload で丸ごと差し替える。
listener からの参照は `JobRegistry#all()` あるいは job id 経由で解決する。

**TagResolver**：`#minecraft:undead` などのタグを、Paper の `Registry.tags()` 経由で `Set<Key>` に resolve する。
`LifecycleEvents.SERVER_LOAD` で 1 度初期化し、data pack reload hook で再計算する。
matcher からは同期的な lookup を提供する。

**ShadowDetector**：起動時に `rewards[]` 上位エントリが下位エントリのマッチ集合を完全に覆う shadow を検出する（[ADR-0004](../spec/adr/0004-first-match-wins.md)）。
警告のみで拒否はしない。

**ActionKeyDeriver**：`MatchCriteria` → 派生キー文字列。
`spec/03-action-detection.md` の対応表を実装で表現する。リストは要素をソートしてから `[a|b|c]` に整形する。

### detection

**EventDispatcher**：全 listener の呼び出し先。「イベント → `DetectedAction`（`JobId`, `RewardEntry`, `amount`, `sourceFlags`）→ `RewardPipeline`」の 3 段を仲介する。
matcher と pipeline の間を細くし、listener の中に業務ロジックを混ぜないようにする。

**DetectedAction**：record。`player`, `matchedJobId`, `matchedEntry`, `derivedKey`, `amount`, `sourceFlags`（`viaTnt`, `spawnerOrigin`, `viaRecentlyPlaced`, ...）を持つ。
「マッチ確定直後、以降のパイプラインの入力」を型で明示する。

#### native\_

Track A の listener を「1 イベントタイプ 1 クラス」で分ける。
どの listener も次のパターンを共有する。

1. Bukkit イベントを受ける。
2. `MatchContext` を組む（entity key、block key、treasure、level、amount、TNT 起爆者、Player 発火可否、etc.）。
3. プレイヤーの現専業を `SpecialtyService#currentJob(player)` で解決。
4. `RewardMatcher#firstMatch(job, actionType, context)` を呼ぶ。
5. マッチしたら `EventDispatcher#dispatch(DetectedAction)` に投げる。

これにより「1 listener の中でパイプラインを直接呼ぶ」ことは避け、パイプライン側にすべての段階を集約する。

- `EntityKilledListener`：`EntityDeathEvent`。killer が Player の場合のみ処理。`Entity.getEntitySpawnReason()` は listener では読まず、パイプラインの段階 3 に載せる。
- `BlockBreakListener`：`BlockBreakEvent`。作物判定 (`Ageable`), TNT 起爆判定 (`sourceFlags.viaTnt`), 直近配置判定 (`sourceFlags.viaRecentlyPlaced`) は listener の中で決めない。listener はイベント値をそのまま context に載せる。TNT 由来の合成 `BlockBreakEvent` は `EntityExplodeEvent` 側の合流 listener が組む。
- `BlockPlaceListener`：報酬パイプラインの入口と同時に、`PlantedFlagWriter`（作物）と `PlacementRecorder`（`recently_placed_break`）にマーキングを依頼する。マーキングは pipeline の segment ではなく listener で行う（ADR-0016 は「BlockPlaceEvent で KVS に書く」と明記）。
- `FishListener`：`PlayerFishEvent.State.CAUGHT_FISH` と `CAUGHT_ENTITY` に限定。`treasure` 判定は `getCaught()` のアイテム内容を fishing/treasure ループテーブル相当集合と照合。
- `FurnaceExtractListener`：Furnace/BlastFurnace/Smoker 共通の `FurnaceExtractEvent`。`getItemAmount` を amount に流し込む。
- `CraftListener`：`CraftItemEvent`（Player click のみ）。シフトクリック時の取り出し個数を計算して amount に流す。
- `EnchantListener`：`EnchantItemEvent`。`enchantment` と `level_min` は matcher 側で評価する。
- `RepairListener`：Anvil の `InventoryClickEvent`（Anvil UI の result slot 取り出し）と `PlayerItemMendEvent` を統合。`source: anvil / mending` を context に載せる。
- `BreedListener`：`EntityBreedEvent`。`getBreeder` の Player 判定はパイプライン段階 3 の `BreedNonPlayerBreederCheck` に載せる（listener で弾かない）。
- `TameListener`：`EntityTameEvent`。
- `ShearListener`：`PlayerShearEntityEvent`。
- `ConsumeListener`：`PlayerItemConsumeEvent`。`category: food / drink` はアイテムメタから判定。
- `VillagerTradeListener`：Merchant の result slot への `InventoryClickEvent`。取引個数を amount に流す。
- `BrewListener`：`BrewEvent`。出力 slot 数 (0〜3) を amount に流す。

#### advancement

**AdvancementListener**：`PlayerAdvancementDoneEvent` を受け、`Key` の namespace が `jobs` の場合のみ処理する。
最後に `AdvancementProgress.revokeCriteria()` を段階 12 で呼ぶ責務は `pipeline.stage.AdvancementRevokeStage` に移す（`PipelineContext.advancementProgress` として持ち運ぶ）。

**AdvancementDatapackInstaller**：同梱 `data/jobs/advancement/*.json` をサーバーの datapack 領域に展開し、`LifecycleEvents` の reload hook で再ロードする。空でも動く。

#### tnt

**TntPrimerTracker**：`PlayerInteractEvent`（flint & steel）、`BlockPlaceEvent`（TNT ブロック）、`BlockIgniteEvent`、`EntityExplodeEvent` を横断。
TNT が primed になった時点で `TNTPrimed` の PDC に起爆者 UUID を書き、`EntityExplodeEvent` で対象 block ごとに合成 `BlockBreakEvent` を発火（`sourceFlags.viaTnt=true`, `sourceFlags.tntPrimer=uuid`）して `BlockBreakListener` パスに流す。
KVS には載せない（PDC のライフサイクルに乗る）。

### matcher

**RewardMatcher**：与えられた `JobDefinition` と `ActionType`、`MatchContext` に対し、`rewards[]` を上から評価して最初にマッチした `RewardEntry` を返す。
first match wins（[ADR-0004](../spec/adr/0004-first-match-wins.md)）。
戻り値には派生キーを付ける（`ActionKeyDeriver` を使う）。

**MatchContext**：ランタイム値。event ごとに listener が組む。`entity: NamespacedKey`, `block: NamespacedKey`, `cropMature: boolean`, `viaTnt: boolean`, `treasure: boolean`, `enchantments: Map<Key, Integer>`, `repairSource: enum`, `consumedCategory: enum`, `advancementKey: Key`。

### pipeline

**RewardPipeline**：段階 1〜12 の実行器。`DetectedAction` を受け取り、`PipelineContext` を build し、`Stage` の list に沿って `execute(ctx)` を呼び続ける。

**PipelineContext**：可変オブジェクト。`player`, `jobId`, `derivedKey`, `matchedEntry`, `amount`, `baseReward`, `finalReward`, `netPaid`, `rareHit`, `sourceFlags`, `advancementProgress?`, `zeroReasons`（監査用）。
`baseReward` / `finalReward` / `netPaid` は `double` で保持する（[ADR-0019](../spec/adr/0019-decimal-reward.md)）。
Stage は必要に応じて field を書き換える。

**stage/**：それぞれの Stage クラス（下記 12 個）。すべて `Stage#execute(PipelineContext ctx) -> StageResult` の interface に従う。
`StageResult` は `CONTINUE` / `HALT`（Matcher で不一致、Specialty で不一致）を区別する。

- `MatcherStage`：`DetectedAction` の段階では既にマッチ済みなので、この Stage は「listener 側で走った matcher の結果を PipelineContext に載せ替える」役目のみ。
  空 stage にせず「Matcher の結果を明示的に確定させる」役として残す（レビュー時に段階 1 の存在が読める）。
- `SpecialtyStage`：`SpecialtyService#currentJob(player)` を再確認。listener 時と pipeline 実行時の間に専業が変わっていた稀ケースを潰す。
- `AntiAutomationStage`：`AntiAutomationCoordinator` を呼び、0 確定なら `PipelineContext.finalReward = 0` を確定させ、以降の Stage は 0 を維持する（Stage 側で `if (ctx.zeroLocked) return CONTINUE;` チェック）。
- `BaseRewardStage`：`RewardAmount` の解決と `amount` の掛け算。amount 解釈は spec 04 のとおり。値は丸めず `double` のまま。
- `RareRollStage`：`java.util.random.RandomGenerator` を使い、`rare.chance` でロール。ヒット時は base を rare で置き換え、`ChatBroadcast` を発火。
- `BuiltinModifierStage`：`VarietyPenaltyEvaluator` → `DailyCapEvaluator` の順で呼ぶ。
- `ExtensionModifierStage`：`ExtensionModifierChain#apply(ctx)` を呼ぶ。優先度順。個別 Modifier が例外時は skip してログ。
- `SplitterStage`：`SplitterChain#apply(ctx)` を呼ぶ。宣言順。`netPaid` を確定させる。
- `RewardRoundingStage`：`config.reward.decimals` と `config.reward.roundingMode` で `baseReward` / `finalReward` / `netPaid` をまとめて丸める（[ADR-0019](../spec/adr/0019-decimal-reward.md)）。以降の Stage は丸め後の値だけを扱う。
- `EconomyTransferStage`：`VaultEconomyAdapter#deposit(player, netPaid, reason, tag)`。0 のときは skip。
- `ActionLogStage`：`ActionLogWriteQueue#enqueue(row)`。行動ログの書き込みは async 化。
  非同期化されたあと、成功時に `JobActionPaidEvent` を async event として発火する（[06-public-api.md](../spec/06-public-api.md)）。
- `AdvancementRevokeStage`：`ctx.advancementProgress != null` のときのみ、`revokeCriteria()` を実行。main thread 保証。

### antiautomation

**AntiAutomationCoordinator**：`JobDefinition.antiAutomation` の enabled フラグを見て、有効な check を順に評価する。
どれかが 0 判定したら `zeroReasons` を追記し、直ちに終了。パイプライン側で `finalReward = 0` を確定する。

**SpawnerOriginCheck**：`entity_killed` のとき `Entity.getEntitySpawnReason() == SPAWNER` で 0。

**UnplantedCropCheck / PlantedFlagWriter**：`block_broken` かつ `Ageable` のとき、ブロックの `PersistentDataContainer` から「植えた」フラグを読む。
`PlantedFlagWriter` は `BlockPlaceListener` から呼ばれ、置いたブロックの PDC に `NamespacedKey("jobs", "planted_by_player")` を書き込む。

**RecentlyPlacedBreakCheck / PlacementRecorder**：`block_broken` のとき、KVS の `place:<world-uuid>:<x>:<y>:<z>` を `get` し、残っていれば 0。
`PlacementRecorder` は `BlockPlaceListener` から呼ばれ、`Ageable` 以外のブロックについて KVS に書く。TTL は config の `window_sec`（デフォルト 3600）。

**AutoFedProcessingCheck / OperatorTracker / ContainerKind**：`item_smelted` と `item_brewed` のとき、KVS の `op:<container-kind>:<coords>` を `get` し、`operator_uuid` が null または未登録なら 0。
`OperatorTracker` は `InventoryClickEvent` と `InventoryMoveItemEvent` を購読し、Player 由来なら operator を書き、hopper/dispenser 由来なら operator を null で上書きする。
`ContainerKind` は Furnace / BlastFurnace / Smoker / BrewingStand の 4 値 enum で固定。

**VillagerRepeatTradeCheck / TradeRecorder**：`villager_traded` のとき、KVS の `trade:<villager-uuid>:<recipe-index>` の `last_traded_at` を確認。残っていれば 0。
`TradeRecorder` は `VillagerTradeListener` の取引成立で書く。TTL は `cooldown_sec`（デフォルト 60）。

**BreedNonPlayerBreederCheck**：`EntityBreedEvent#getBreeder` が Player でなければ 0。追跡データ不要。

### modifier

#### variety

**VarietyPenaltyEvaluator**：直近 `window` 件のアクションキー分布から最多比率を計算し、`curve` の `up_to` 昇順で最初にマッチする multiplier を採用する。
`hide_numbers: true` のとき、内部ログには数値を残すが player 通知は `disclosed_message` のみ。

**VarietyRingBuffer**：`(playerUuid, jobId)` ごとに `int size = window` の ring buffer を持つ。
プレイヤーログイン時に `ActionLogRepository.distinctKeys` ではなく生ログの最新 `window` 件を取ってきて初期化する（追加のリポジトリメソッドが必要 → `ActionLogRepository#recentKeys(player, jobId, limit)` として追加）。

**VarietyCurveLookup**：`List<CurvePoint>` を昇順ソート済みで受け取り、`lookup(ratio)` で multiplier を返すヘルパ。

#### dailycap

**DailyCapEvaluator**：`config.daily_cap` の `amount`, `scope` (`total`, `per_job`), `reset_at` を見て、当日累計を `DailyTotalCache` から取り、`amount` を超える場合は超過分だけ削る。
0 に達している場合は 0。
削った分は削っただけ pipeline 側の `PipelineContext.zeroReasons` に「daily_cap_hit」として残す。

**DailyTotalCache**：`Map<(playerUuid, date, jobId?), long>` の in-memory cache。プレイヤーログイン時に MySQL から当日行を読み込む。
`ActionLogStage` の書き込みと同期して increment する。

**ExtensionModifierChain**：`api.extension.JobRewardModifier` の登録簿。
`register(JobRewardModifier)`, `unregister(String id)`, `apply(PipelineContext)` を持つ。
`getPriority()` の昇順で走らせる。個別 Modifier の例外はキャッチしてログに記録し、次に進む。

### splitter

**SplitterChain**：`api.extension.JobRewardSplitter` の登録簿。宣言順に `split(ctx)` を呼び、`Split.deductedFromPlayer` を累積して `netPaid` を確定させる。
各 `Transfer` は Splitter の内部で `VaultEconomyAdapter` を叩く（Job プラグイン側は口座種別を知らない）。
Splitter の例外はキャッチしてログに記録し、次に進む。

### economy

**VaultEconomyAdapter**：Vault 経由の `Economy#depositPlayer(player, amount)` を wrap する。
起動時に Vault が有効か確認し、ない場合は起動失敗にする（`paper-plugin.yml` で `required: true`）。
`transfer(from, to, amount, reason, tag)` の抽象を提供し、`JobRewardSplitter` の Transfer にも同じ interface を提供する。

### specialty

**SpecialtyService**：現在の専業取得、初回選択、変更を扱う。
- `currentJob(playerUuid) -> Optional<JobId>`
- `select(playerUuid, jobId) -> SpecialtyChangeResult`（初回選択、`PlayerJob` に insert、`JobSpecialtyChangedEvent` を発火）
- `change(playerUuid, newJobId) -> SpecialtyChangeResult`（クールダウン判定 → 成功時 insert & event）
- 起動時にプレイヤーログインで `player_job` の最新行をキャッシュに読む。

**CooldownPolicy**：`config.specialty_mode.change_policy` を評価する。
上から評価し、`within` にマッチした最初のポリシーの `cooldown` を採用。`default` はフォールバック。

**SpecialtyChangeResult**：sealed。`Success(JobId prev, JobId next, Instant at)`, `CooldownRemaining(Duration remaining, Instant nextAvailable)`, `NoChange`。

### persistence

**PlayerJobRepository / ActionLogRepository / DailyRewardTotalRepository**：interface のみ。
シグネチャは [05-persistence.md](../spec/05-persistence.md) に従う。

**dto/**：`PlayerJobRow`, `ActionLogRow`, `ActionFilter`, `TimeRange`, `DailyRewardDelta`。
公開 API の `api.query.ActionFilter` はここを import して同じ型を使う（重複型を作らない）。

**async/ActionLogWriteQueue**：`java.util.concurrent.ArrayBlockingQueue<ActionLogRow>`。
`enqueue(row)` は main thread から呼ばれる、非ブロッキング。
プラグイン停止時は `drain(timeout)` を提供し、`shutdown()` に組み込む。

**async/BatchFlushWorker**：単一 daemon スレッド。1 秒または 1000 件で `insertBatch`。同じバッチで `DailyRewardTotalRepository#addBatch` も走らせる。
失敗時はリトライ。連続失敗で `Logger.severe`、`ActionLogStage` の enqueue にバックプレッシャを掛ける（キューが満杯なら main thread の pipeline は 0 バッファ挙動＝ログ落とし、`Logger.warning` を出す）。

**mysql/MySqlDataSource**：HikariCP のセットアップ。`config.persistence.host / database / pool_size / user / password` を受け取り、`DataSource` を返す。`SELECT 1` ヘルスチェック。

**mysql/SchemaInitializer**：起動時に `CREATE TABLE IF NOT EXISTS` を発行。DDL は `resources/sql/mysql/schema.sql` に置き、読んで実行するだけの単純実装。

**mysql/MySql\*Repository**：`PreparedStatement` を素直に書く（jOOQ、Hibernate は使わない、[ADR-0018](../spec/adr/0018-repository-interface.md)）。

**ActionLogQueryService の実装**：`ActionLogRepository` の上位ラッパーとして `persistence` に置き、`api.query.ActionLogQueryService` interface を implement する。
`CompletableFuture` の wrapper は `util.AsyncExecutor` を経由する。

### kvs

**JobsKVStore**：spec 05 の interface（`put`, `get`, `remove`）。

**KvsKeys**：文字列 key の組み立てを集約。`place(world, x, y, z)`, `op(kind, world, x, y, z)`, `trade(villager, recipeIndex)` の 3 メソッド。

**memory/InMemoryKVStore**：`com.github.benmanes.caffeine.cache.Cache<String, byte[]>` を使い、`expireAfterWrite` を put の TTL 単位で扱う。
Caffeine は Paper に shaded で含まれるため、明示依存を build.gradle には足さない。

### ui

**DialogService**：`BedrockDialog.get()` を wrap し、Bukkit main thread への dispatch と `player.locale()` の解決を提供する。
すべての Dialog 表示はここを経由。

**SpecialtyListDialog**：`MultiButtonDialog`。`Mode.SELECT` / `Mode.CHANGE` / `Mode.INFO` の 3 モードで再利用する統合ダイアログ。全ジョブを列挙し、`CHANGE` では現在の専業を除外する。`INFO` は `/jobs info` 経路で使い、押下は必ず `JobConditionsDialog(READ_ONLY)` を開く。ボタンは `JobDefinition.icon`, `displayName` を使う。
`disclose_before_select: true` のとき、ボタン押下で `JobConditionsDialog` を開く。
`false` のときはボタン押下時に Bukkit scheduler で main thread に戻し、`SpecialtyService#select` / `change` を呼ぶ（[ADR-0014](../spec/adr/0014-bedrock-dialog.md)）。

**SpecialtyCooldownDialog**：`NoticeDialog`。現在の専業と `nextAvailableAt` を body に載せ、「閉じる」だけを持つ。cooldown 中の再選択導線はここから出さない。

**JobConditionsDialog**：`MultiButtonDialog`。単職の条件を 1 画面にまとめ、`Mode` により挙動を切り替える（`PREVIEW_FOR_SELECT` / `PREVIEW_FOR_CHANGE` / `READ_ONLY`）。
body は `JobConditionsFormatter` が組み立てる。「一覧に戻る」ボタンは `SpecialtyListDialog` を開き直す。

**JobConditionsFormatter**：`JobDefinition` の `rewards[]` / `variety_penalty` / `anti_automation` / `description` を human-readable な `Component` に整形する。`config.specialty_mode.disclose_reward_amount` に従い額の表示を切り替える。

**StatusDialog**：`NoticeDialog`。現在専業、当日獲得額、単調性ペナルティ状態、次回変更可能時刻。
`hide_numbers: true` のときは `disclosed_message` のみ表示。

**DialogTexts**：i18n key の集約。`dialog.select.title`, `dialog.change.body.currentJob`, ...。定数として持ち、i18n の欠損を静的に検出しやすくする。

### i18n

**LocaleRegistry**：`resources/lang/*.yml` および `plugins/Jobs/lang/*.yml`（後者が優先）から key → value を読み込む。
`ja_jp` を必須、他は任意。差分 key を起動時に警告。

**I18n**：`I18n#format(player, key, TagResolver...)` → `Component`。MiniMessage の `TagResolver` で動的値を埋め込む。
Bedrock 側では BedrockDialog が MiniMessage を strip するため、文言は装飾を外しても意味が通るようにする（テストでも装飾を落とした形で検証する）。

**MissingKeyReporter**：起動時に `ja_jp` の全 key に対して各ロケールの欠損を報告する。

### command

**JobsCommands**：`Commands.literal("jobs")` のツリー構築。`select`, `info`, `status`, `reload` のサブコマンドを持つ。
`LifecycleEvents.COMMANDS` で登録（[paper/modern-commands.md](../paper/modern-commands.md)）。

**SelectSub**：`/jobs select`。現在の専業状態で分岐する。未選択なら `SpecialtyListDialog(Mode.SELECT)`、選択済み & cooldown 中なら `SpecialtyCooldownDialog`、選択済み & 変更可なら `SpecialtyListDialog(Mode.CHANGE)`。旧 `/jobs change` はこれに統合して削除する。

**InfoSub**：`/jobs info [<job-id>]`。純閲覧経路。引数なしなら全ジョブを列挙する `MultiButtonDialog`（押下で `JobConditionsDialog(READ_ONLY)`）、引数ありなら該当ジョブを直接 `JobConditionsDialog(READ_ONLY)` で開く。選択・cooldown 状態に関係なく実行可。

**StatusSub**：`/jobs status`。ステータスをチャットに MiniMessage で出力する。

**ReloadSub**：`/jobs reload`。YAML、lang、tag cache、advancement datapack を再読込。permission required。

### util

- `UuidBytes`：`UUID <-> byte[16]`。
- `AsyncExecutor`：`runAsync(Runnable)`, `supplyAsync(...)`, main thread への `runOnMain(Runnable)`。BedrockDialog callback の main thread ディスパッチにも使う。
- `MiniMessages`：`MiniMessage.miniMessage()` の singleton wrapper。

## 依存関係

`api/` は他のパッケージに一切依存させない（interface と record 、および `Player`, `Instant`, `UUID` のような JDK / Paper 型のみ）。

内部側の依存はおおよそ次のレイヤ。

```
detection.*, command.*
    ↓                          ↑ 上向き逆流禁止
matcher, pipeline, specialty, ui, i18n
    ↓
registry, antiautomation, modifier, splitter, economy
    ↓
persistence, kvs
    ↓
domain, config, util
```

`api.*` は `domain`, `util` を参照してよい（`JobId`, `Instant` 相当）。逆はしない。

## 公開 API と内部型の重複回避

`api.query.ActionFilter` と `persistence.dto.ActionFilter` は同一クラスを使う。
仕様書は「引数・戻り値に使う DTO 型」を repository と共有すると明記している（[ADR-0018](../spec/adr/0018-repository-interface.md)）。
そこで DTO の物理的な置き場は `api.query.*`（公開扱い）とし、`persistence.*` は import して使う。
`ActionLogRow` は persistence 側の物理レイヤ寄りなので `persistence.dto.*` に置き、公開しない。

## テスト戦略

- 各パッケージは `src/test/java/me/f0reach/jobs/<pkg>/*Test.java` を持つ。
- パイプラインの Stage は個別に `PipelineContext` を渡すだけで単体テスト可能。
- listener は MockBukkit（既に build.gradle に入っている）で単体テスト。
- MySQL 実装は Testcontainers か H2 でなく、`mysql-connector-j` を local MySQL 相手に叩く integration test を別スイート（`src/test-integration/`）で分離する余地。Phase 1 は in-process H2 か MockBukkit で最低限のリポジトリ動作確認までとする。

## 関連文書

- [phases.md](./phases.md)　実装フェーズと着手順序
- [threading.md](./threading.md)　スレッドモデルとライフサイクル
- [spec/01-overview.md](../spec/01-overview.md)
- [spec/04-reward-pipeline.md](../spec/04-reward-pipeline.md)
