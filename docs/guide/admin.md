# 管理者ガイド

Jobs プラグインをサーバに導入・運用する管理者向けのガイド。
利用者向けの説明は [player-basic.md](./player-basic.md)、[player-advanced.md](./player-advanced.md) を参照する。

内部仕様の詳細や設計判断（ADR）は [../spec/](../spec/) を参照する。ここでは「動かすために何を触るか」を中心にまとめる。

## 対象環境と依存

- **サーバ**：Paper 1.21 以降（[../spec/adr/0008-paper-1-21-only.md](../spec/adr/0008-paper-1-21-only.md)）
- **必須プラグイン**：
  - `Vault`（Economy 送金の窓口。Vault 対応 Economy プラグインが別途必要）
  - `BedrockDialog`（Java/Bedrock 両対応ダイアログ。Paper DialogAPI と Geyser フォームを自動で切り替える）
- **任意プラグイン**：`PlaceholderAPI`（詳細は [PlaceholderAPI 連携](#placeholderapi-連携) 参照）
- **RDBMS**：MySQL（現行 Phase 1 の唯一の永続化バックエンド。[../spec/adr/0009-mysql-persistence.md](../spec/adr/0009-mysql-persistence.md)）

Vault 対応 Economy と MySQL は事前に用意しておく。

## インストール

1. `Jobs.jar` を `plugins/` に置く。依存する `Vault`、`BedrockDialog`、Vault 対応 Economy プラグインも `plugins/` に揃える。
2. サーバを起動する。初回起動時、以下が自動で生成される。
   - `plugins/Jobs/config.yml`（グローバル設定）
   - `plugins/Jobs/jobs/`（同梱サンプル職業 5 種：`combat.yml` / `mining.yml` / `farming.yml` / `crafter.yml` / `explorer.yml`）
   - `plugins/Jobs/lang/`（`ja_jp.yml` と `en_us.yml`）
3. サーバを停止し、`config.yml` の `persistence` 節に MySQL 接続情報を記入する（後述）。
4. サーバを再起動する。テーブル作成マイグレーションが自動で走る。
5. `/jobs info` を実行して職業一覧ダイアログが開けば導入完了。

## `config.yml` の要点

グローバル設定は `plugins/Jobs/config.yml` に置く。全項目の仕様は [../spec/02-yaml-schema.md](../spec/02-yaml-schema.md) の「グローバル設定」参照。運用でよく触るポイントは次のとおり。

### `specialty_mode`

```yaml
specialty_mode:
  reward_non_specialty: 0.0
  show_select_dialog_on_join: true
  disclose_before_select: true
  disclose_reward_amount: true
  change_policy:
    - within: { event_hours: [0, 24] }
      cooldown: 1h
    - default:
      cooldown: 5d
```

- **`reward_non_specialty`**：`0.0` 固定推奨。専業外の報酬倍率で、`0.0` のとき報酬支払いも行動ログ書き込みも行わない。
- **`show_select_dialog_on_join`**：未選択プレイヤーがログインした直後に選択ダイアログを自動表示するか。ロビーサーバや別の職業案内 UI がある環境では `false` にする。
- **`disclose_before_select`**：職業一覧のボタンを押したときに、即確定せず条件開示ダイアログを挟むか。`true` 推奨。
- **`disclose_reward_amount`**：`/jobs info` などで報酬額を表示するか。イベント演出で額を伏せたい場合のみ `false`。rare の chance/reward はこのフラグに関わらず常に非表示。
- **`change_policy`**：専業変更クールダウン。上から評価し、最初にマッチした `within` 条件が適用される。`default` はフォールバック。時間単位は `1h` / `5d` / `30m` のような書式。イベント開催中を「先着 24 時間だけ短縮」にするような制度に向く。

### `reward`

報酬額の丸め設定（[../spec/adr/0019-decimal-reward.md](../spec/adr/0019-decimal-reward.md)）。

```yaml
reward:
  decimals: 0
  rounding_mode: HALF_UP
```

- **`decimals`**：小数点以下の桁数。`0` で整数のみ。上限 `6`。
- **`rounding_mode`**：`java.math.RoundingMode` の名称。`HALF_UP` / `HALF_EVEN` / `DOWN` など。

### `daily_cap`

```yaml
daily_cap:
  amount: 1000000
  reset_at: "00:00"
  scope: total
```

- **`amount`**：1 日あたりの報酬上限。`0` にすると事実上無効化。
- **`reset_at`**：リセット時刻（サーバ時間）。
- **`scope`**：`total`（全職業合算）または `per_job`。`per_job` は職業変更や自動化対策の絡みで挙動が複雑になるため、初期運用は `total` を推奨。

### `persistence`

```yaml
persistence:
  type: mysql
  host: localhost
  port: 3306
  database: jobs
  user: jobs
  password: ""
  pool_size: 8
  retention_days: 30
```

MySQL への接続情報。`retention_days` は `action_log` テーブルの保持日数で、それより古い行は日次のパージで削除される。監査要件がある場合は長めに設定する。テーブル定義や運用の詳細は [../spec/05-persistence.md](../spec/05-persistence.md)。

### `anti_automation`

各自動化対策のグローバルデフォルトと、ActionBar 通知設定。

```yaml
anti_automation:
  # spawner_origin_kills: zero
  # unplanted_crop_harvest: zero
  # recently_placed_break:
  #   window_sec: 3600
  # auto_fed_processing:
  #   operator_ttl_sec: 60
  # villager_repeat_trade:
  #   cooldown_sec: 60
  #   scope: recipe
  # breed_non_player_breeder: zero

  notify:
    action_bar:
      spawner_origin_kill: true
      unplanted_crop_harvest: true
      recently_placed_break: true
      auto_fed_processing: true
      villager_repeat_trade: true
      breed_non_player_breeder: true
```

- **有効化のパターン**：
  - `zero` を書くと有効化（デフォルト値のセクションで動く）。
  - セクションで詳細指定するとカスタム値で有効化。
  - `off` を書くと明示的に無効化。
  - 行そのものを消すと「デフォルトなし」で、per-job で opt-in された場合のみ動く。
- **per-job override**：職業 YAML の `anti_automation` セクションで同名キーを書くと per-job 値が勝つ。
- **`notify.action_bar`**：0 判定発火時にプレイヤーへ理由を通知するか。reason ごとに個別に切れる。per-job override は無い（グローバル固定）。

## 職業 YAML の書き方

`plugins/Jobs/jobs/<job-id>.yml` に 1 ジョブ 1 ファイルで配置する。
最小フォーマットは [../spec/02-yaml-schema.md](../spec/02-yaml-schema.md)、`on` ごとの match フィールドは [../spec/03-action-detection.md](../spec/03-action-detection.md) を参照する。

より広い Item / Mob カバレッジで書いた実例集は [../examples/](../examples/) にある。以下は要点だけ抜粋する。

### 最小例

```yaml
id: combat
display_name: "討伐"
description: "敵性 mob を討伐して稼ぐ。"
icon: minecraft:iron_sword

rewards:
  - on: entity_killed
    entity: minecraft:zombie
    reward: 5

  - on: entity_killed
    entity: minecraft:skeleton
    reward: 5
    rare:
      chance: 0.001
      reward: 100000
      announce: "<gold><player> が黄金スケルトンを討伐した！</gold>"

variety_penalty:
  enabled: true
  window: 30
  curve:
    - { up_to: 0.40, multiplier: 1.00 }
    - { up_to: 0.60, multiplier: 0.90 }
    - { up_to: 0.80, multiplier: 0.75 }
    - { up_to: 1.00, multiplier: 0.50 }
    - { up_to: 1.01, multiplier: 0.30 }
  disclosed_message: "同じものばかり扱うと効率が落ちる"
  hide_numbers: true

anti_automation:
  spawner_origin_kills: zero
```

### 押さえるべきポイント

- **`id`**：ASCII 小文字と `_`。行動ログとの紐付けキーになるため、運用開始後は変えない。
- **`rewards[].on`**：`entity_killed` / `block_broken` / `block_placed` / `item_fished` / `item_smelted` / `item_crafted` / `item_enchanted` / `item_repaired` / `entity_bred` / `entity_tamed` / `entity_sheared` / `item_consumed` / `villager_traded` / `item_brewed` / `advancement`。使えるフィールドと amount の解釈は [../spec/03-action-detection.md](../spec/03-action-detection.md) を参照。
- **`reward`**：固定値 `5`、範囲乱数 `{ min: 1, max: 3 }`、小数（`0.5`）も可。小数は末尾の `reward.decimals` / `rounding_mode` に従い一括で丸められる。
- **タグ指定**：`entity: "#minecraft:undead"` のように registry tag を書ける。データパックで追加した tag も透過的に扱える。
- **first match wins**：`rewards` 配列は上から評価される。汎用エントリ（タグ）を上に置くと、下の特化エントリが影に隠れる。起動時に shadow 検出の警告ログが出るので、`server.log` を確認する。
- **派生キー**：報酬エントリごとに `kill:minecraft:zombie` のような識別子が振られ、行動ログの `action_key` と単調性ペナルティのバケットに使われる。OR リストやタグでまとめると 1 バケットに集約される。バケットを分割したければエントリを分ける。

### 適用と reload

YAML を追加・変更した後は次のいずれかで反映する。

- `/jobs reload`：ジョブ、lang、tag cache、advancement datapack をまとめて再読込。
- サーバ再起動。

reload に失敗すると `chat` にエラーが返り、旧設定のまま動作を続ける。起動時ログにもスタックトレースが出るので、YAML 構文エラーはそこで確認する。

## コマンドリファレンス

利用者向けコマンドは [player-basic.md](./player-basic.md) にまとめてある。管理者専用は以下。

### `/jobs reload`

YAML / lang / tag cache / advancement datapack をすべて再読込する。ジョブ数と報酬エントリ数がチャットに返る。
YAML の構文エラーがあれば失敗メッセージが返り、旧設定のまま動作継続する。

### `/jobs admin inspect <player>`

対象プレイヤーの現状を出す。オフラインでも DB から読める。

- 現在の専業
- cooldown 起点時刻と次回変更可能時刻
- 当日累計獲得額
- 単調性 ring buffer のスナップショット（memory 常駐なのでオフライン時は「取得不可」）

「あの人本当に○○職？」を確認したいときの一次情報。

### `/jobs admin actions <player> [<hours>] [<limit>]`

対象プレイヤーの直近アクションログを表示する。「報酬が入っていない」問い合わせの一次切り分けに使う。

- `<hours>`：何時間前まで遡るか。デフォルト 1。上限 720（30 日）。
- `<limit>`：最大件数。デフォルト 20。上限 100。
- 各行の末尾に `[rare]` が付いていれば rare 発火。
- クエリは async で走るので、大きい `<limit>` でもメインスレッドは止まらない。

例：`/jobs admin actions Steve 24 100` で過去 24 時間の最大 100 件を出す。

### `/jobs admin stats [<job>]`

集計情報を出す。当日 0 時からのウィンドウ。

- 引数なし：職業ごとの在籍人数一覧、当日総支払、アクション数、rare ヒット率。
- `<job>` 指定：指定職業に絞って同様の集計。

登録済み職業はプレイヤー 0 でも並ぶ（列挙は `JobRegistry` の順序）。

### `/jobs admin set <player> <job>`

対象プレイヤーの専業を強制的に変更する。cooldown を無視する。
`player_job_history` に `actor='admin'` で 1 行 append される（監査ログ）。
オフライン相手でも実行可能。

イベント運用で「途中参加者の初期専業を管理側で決める」ような場面で使う。

### `/jobs admin reset-cooldown <player>`

対象プレイヤーの変更クールダウンをクリアする。`cooldown_base_at` を `Instant.EPOCH` に上書きするだけの副作用最小の operation。専業自体は変わらないので `player_job_history` に行は入らない。

「間違って選んだのですぐ変えたい」プレイヤーサポート用。

### `/jobs admin pay <player> <amount> [<reason>]`

パイプラインを通さずに直接支給する。Vault Economy に deposit し、`action_log` に `action_key='admin:manual'` で行を残す。
`<reason>` はサーバログの監査経路に記録される（チャットには単に付加表示）。

- 単調性・日次キャップ・自動化対策の判定を経ないため、いくらでも支給可能。
- オンラインなら `JobActionPaidEvent` を発火するので、Quest プラグイン等の連携先が手動支給を認識する。
- 金額は正の数のみ。負値・0 は拒否される。

### `/jobs admin reset-daily-cap <player>`

対象プレイヤーの当日日次キャップ累計をリセットする。`daily_reward_total` の当日 row を削除し、`DailyTotalCache` の該当 entry を 0 に戻す。

**注意**：`scope: per_job` の場合、次回ログイン時に `action_log` から再計算されて元に戻ることがある。恒久リセットではなく「今この瞬間の一時解除」に近い挙動。

### `/jobs admin reset-variety <player>`

単調性ペナルティの ring buffer をクリアする。memory-only なので、オンラインのプレイヤーにのみ適用可能。オフラインの場合はエラー返却。

「特定プレイヤーのペナルティを解除して即再挑戦させたい」用途。

### `/jobs admin flush`

`BatchFlushWorker` を即時 flush する。通常は定期バッチで書き込まれる `action_log` を強制的にディスクに落とす。

サーバ再起動前や、書き込み障害の切り分け（キューに残っていないか）に使う。書き込み件数がチャットに返る。

## パーミッション

`paper-plugin.yml` で全ノードを宣言している。詳細と適用箇所は [../spec/08-permissions.md](../spec/08-permissions.md)。

### コマンド系（default: true）

| ノード | 用途 |
|---|---|
| `jobs.command.use` | `/jobs` ツリーそのものの入口 |
| `jobs.command.select` | `/jobs select` |
| `jobs.command.info` | `/jobs info` |
| `jobs.command.status` | `/jobs status` |

未権限時は Brigadier の `.requires` によりタブ補完から除外され、実行すると Paper 標準の "Unknown command" が返る。ジョブ制度の存在を伏せた運用と噛み合う。

### 管理系（default: op）

親ノード `jobs.admin` に `children:` として全管理コマンドを束ねてある。上位ランクへの一括付与に使える。個別にも切り分けできる。

`jobs.admin.reload` / `jobs.admin.inspect` / `jobs.admin.stats` / `jobs.admin.actions` / `jobs.admin.set` / `jobs.admin.reset-cooldown` / `jobs.admin.pay` / `jobs.admin.reset-daily-cap` / `jobs.admin.reset-variety` / `jobs.admin.flush`

### バイパス系（default: false）

報酬パイプラインの各ゲートを個別に無視する権限。イベント時のスタッフやテスト作業に付与する。

| ノード | 効果 |
|---|---|
| `jobs.bypass.specialty` | 専業外アクションでも報酬・ログ発生 |
| `jobs.bypass.anti-automation` | 自動化対策 6 種を通過 |
| `jobs.bypass.daily-cap` | 日次キャップを無視 |
| `jobs.bypass.variety-penalty` | 単調性ペナルティ倍率 1.0 固定（ring buffer 更新は継続） |
| `jobs.bypass.cooldown` | 専業変更クールダウンを無視 |
| `jobs.bypass.*` | 上記すべてを一括付与 |

バイパス発動時も行動ログには通常どおり書き込まれる。監査で追跡可能。

### 権限管理プラグインとの相性

Brigadier `.requires` の評価はコマンドツリー配信時なので、LuckPerms 等で動的に付け外しした場合、通常は権限管理プラグイン側が `Player#updateCommands()` を自動で呼ぶ想定に乗る。Jobs 側からは明示的に呼ばない。

バイパス系は毎回の pipeline 実行で `hasPermission` を評価するため、権限変更は次のアクションから即時反映される。

## 多言語対応

UI 文言は `plugins/Jobs/lang/<locale>.yml` に MiniMessage 形式で置く。同梱は `ja_jp.yml` と `en_us.yml`。

- ファイル名は Minecraft のロケール ID に合わせる（`ja_jp.yml`、`en_us.yml`、`ko_kr.yml` 等）。
- キーは階層化されている（`dialog.specialty.title`、`command.status.current` 等）。
- プレイヤーの locale は `Player#locale()` で解決。対応ファイルがなければ `ja_jp` にフォールバック。
- 起動時にロケール間のキー差分を警告ログとして出す。ロケール追加時はこのログを見て漏れを埋める。

**装飾に依存しない**：Bedrock 側では MiniMessage 装飾が strip されるため、色分けだけで「変更不可」を示すような書き方は避け、文言そのもので伝わるように書く。

**動的プレースホルダ**：`<player>`、`<amount>`、`<cooldown>` などが翻訳文字列に含まれる。これらは Dialog 構築時に MiniMessage の `TagResolver` で解決される。詳細は [../spec/07-ui.md](../spec/07-ui.md)。

**注意**：ジョブ YAML の `display_name`、`description`、`variety_penalty.disclosed_message`、`rewards[].rare.announce` は翻訳ファイルには置かず、値そのものが MiniMessage として解釈される。多言語対応が必要な場合は、イベント側プラグインの拡張点で切り分ける（Job プラグイン本体では規定しない）。

## データパック advancement（Track B）

Bukkit イベントだけでは表現しづらい複合条件（帯電クリーパーを剣で倒す、特定 biome で特定アイテムを得る、など）は、データパックの advancement を経由して検知する（[../spec/03-action-detection.md](../spec/03-action-detection.md)）。

- 配置先：同梱データパック `data/jobs/advancement/`。
- 制約：hidden、親なし、1 criterion。namespace は `jobs`。
- ジョブ YAML では `on: advancement`、`advancement: jobs:combat/charged_creeper_sword_kill` のように参照する。
- 発火後に `AdvancementProgress.revokeCriteria()` で criterion を取り消し、次の発火に備える（多重ヒット防止）。

`data/jobs/advancement/` を空のまま運用すれば Track B は使わない構成になる（Track A だけで代表的なアクションは網羅可能）。

## 拡張プラグインとの連携

Jobs プラグインは他プラグインが差し込める拡張点を持つ（詳細は [../spec/06-public-api.md](../spec/06-public-api.md)）。

- **`JobRewardModifier`**：報酬パイプラインの段階 7 に差し込む倍率補正。期間ボーナス、ツール強化などに使う。
- **`JobRewardSplitter`**：段階 8 に差し込む分配ロジック。税、社員拠出、団体分配などに使う。
- **`JobActionPaidEvent`**：報酬支払い完了時に発火する Bukkit イベント。Quest プラグインなどが購読する。
- **`ActionLogQueryService`**：行動ログの集計クエリ API。業績指標や達成条件の判定に使う。

イベント固有のルール（21 日の経済設計、イベント固有の職業、期間ボーナス）は Jobs プラグイン本体には持たせず、拡張プラグイン側でこれらの拡張点を通じて差し込む方針。詳細は [../spec/01-overview.md](../spec/01-overview.md)。

## PlaceholderAPI 連携

`PlaceholderAPI` プラグインを導入している場合、Jobs は起動時に自動で `jobs` 拡張を登録する。他プラグインの MiniMessage / チャット / スコアボード表示などで、プレイヤーの現在の職業を差し込める。

| Placeholder | 内容 | 未選択・オフライン時 |
|---|---|---|
| `%jobs_current_id%` | 現在の職業 ID（例: `combat`） | 空文字 |
| `%jobs_current_name%` | 職業 YAML の `display_name`（MiniMessage 素の値） | 空文字 |
| `%jobs_has_job%` | 選択済みなら `true`、未選択なら `false` | `false` |

- オフラインプレイヤーはキャッシュに乗っていないため、常に「未選択」と同じ扱いになる（DB へは同期問い合わせしない）。オンラインのプレイヤー UI 用途を想定している。
- 拡張はプラグイン内蔵型で、`/papi reload` 後も持続する。
- PlaceholderAPI 未導入の環境では拡張登録をスキップするだけで、Jobs 本体の動作には影響しない。

## トラブルシュート

### 「プレイヤーから『報酬が入らない』と問い合わせがきた」

1. `/jobs admin inspect <player>` で専業と当日累計を確認。
2. `/jobs admin actions <player> 24 50` で直近 24h のアクションログを見る。
3. ログに行が残っているのに `reward` が 0 なら自動化対策発動（アクションバー通知が出ているはず）。
4. ログに行が無いなら専業判定ではじかれている（違う職業のアクションを試みた）か、マッチ条件に該当していない。ジョブ YAML の `rewards` 一覧を確認する。

### 「起動時に shadow の警告が出る」

サーバログに `first match wins` の shadow 検出が出ることがある。汎用エントリ（タグや広い OR リスト）を上に置いていて、下の特化エントリが完全に覆い隠されている状態。エントリ順を並べ替えるか、汎用エントリの対象を絞る。

### 「MySQL に接続できない」

`config.yml` の `persistence` 節を確認する。ホスト・DB 名・ユーザー・パスワードのミス、DB 側の権限（`CREATE TABLE`、`INSERT`、`SELECT`、`DELETE`）が不足していないか。HikariCP のログに詳細が出る。

### 「/jobs admin flush が遅い」

`action_log` の書き込み待ちが溜まっている可能性。頻度の高いサーバでは pool_size を上げるか、通常運用に任せて明示 flush を控える。

### 「ダイアログが Bedrock 側で崩れる」

装飾に依存した文言を lang ファイルに書いていないか。色分けや hover でしか意味を伝えていない箇所は Bedrock で情報が落ちる。文言そのものに情報を持たせる。

## 参考文書

- [../spec/01-overview.md](../spec/01-overview.md)　プラグイン全体像と設計スコープ
- [../spec/02-yaml-schema.md](../spec/02-yaml-schema.md)　YAML スキーマ完全仕様
- [../spec/03-action-detection.md](../spec/03-action-detection.md)　アクション検知の仕組み
- [../spec/04-reward-pipeline.md](../spec/04-reward-pipeline.md)　報酬パイプラインの各段階
- [../spec/05-persistence.md](../spec/05-persistence.md)　MySQL スキーマと KVS
- [../spec/06-public-api.md](../spec/06-public-api.md)　拡張プラグイン向け API
- [../spec/07-ui.md](../spec/07-ui.md)　Dialog UI の仕様
- [../spec/08-permissions.md](../spec/08-permissions.md)　パーミッションノード一覧
- [../examples/](../examples/)　より広範なジョブ YAML の実例集
