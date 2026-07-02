# YAML スキーマ

Job プラグインの設定は YAML で記述する。
`config.yml` にはプラグイン全体の設定を、`jobs/<job-id>.yml` には個別ジョブの定義を置く。

## 1 ジョブの最小例

```yaml
# jobs/combat.yml
id: combat
display_name: "討伐"
icon: minecraft:iron_sword

rewards:
  - on: entity_killed
    entity: minecraft:zombie
    reward: 5

  - on: entity_killed
    entity: minecraft:blaze
    reward: 20

  - on: entity_killed
    entity: minecraft:skeleton
    reward: 5
    rare:
      chance: 0.0005
      reward: 100000
      announce: "{player} が黄金スケルトンを討伐した！"

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

## トップレベルフィールド

**id**：ジョブの一意な識別子（ASCII 小文字とアンダースコア）。
変更すると行動ログとの紐付けが切れるため、運用開始後は固定する。

**display_name**：UI 表示に使う日本語名。

**icon**：Dialog や Inventory GUI で使うアイテム種別。
Minecraft の `Material` 名（`minecraft:<id>` 形式）。

**rewards**：報酬定義の配列。
詳細は次節。

**variety_penalty**：単調性ペナルティの設定。
ジョブごとに有効化と曲線を変えられる。
省略時は無効。

**anti_automation**：自動化対策の有効化フラグ。
省略時は無効。

## rewards エントリ

各エントリは「どのイベントで何にマッチしたら、いくら払うか」を 1 件宣言する。
エントリに明示的な ID は付けない（[ADR-0003](./adr/0003-match-without-explicit-id.md)）。
内部的にはマッチ条件から派生キーを生成し、行動ログと単調性ペナルティのバケットに使う。

### 共通フィールド

**on**：トリガとする Bukkit イベントまたは advancement 種別。
取りうる値は [03-action-detection.md](./03-action-detection.md) に列挙する。

**reward**：報酬額。
整数で固定値、または `{ min, max }` で一様乱数の範囲を指定する。

**rare**：低確率ボーナスのオプション。
省略可。

- `chance`：1 アクションあたりの発火確率（0.0〜1.0）。
- `reward`：発火時の報酬。固定値または `{ min, max }`。
- `announce`：発火時にサーバ全体に流すメッセージ。`{player}` がプレイヤー名に置換される。

通常報酬と rare 報酬は同一エントリに同居できる。
rare がヒットしたとき、通常報酬は支払わず rare 報酬で置き換える。

### match フィールド

`on` に応じて使えるフィールドが異なる。
1 つのフィールドは値、リスト、タグのいずれかで指定でき、いずれの指定方法も OR とみなす（[ADR-0005](./adr/0005-or-expression-strategy.md)）。

`entity_killed` のとき。

- `entity`：エンティティ種別。`minecraft:zombie` のような ID、ID のリスト、`#minecraft:undead` のような registry tag。

`block_broken` のとき。

- `block`：ブロック種別。同上の指定方法。
- `crop_mature`：`true` のとき、`Ageable` であって `getAge() == getMaximumAge()` のブロックのみマッチ。
- `via_tnt`：`true` のとき、TNT 起爆に由来する破壊のみマッチ。`false` または省略のとき、TNT 由来は除外する。

`block_placed` のとき。

- `block`：ブロック種別。

`item_fished` のとき。

- `item`：アイテム種別。
- `treasure`：`true` のとき、`PlayerFishEvent` のキャッチが宝箱ループテーブル由来であるもののみマッチ。

`item_smelted` のとき。

- `item`：取り出した精錬物の種別。Furnace / BlastFurnace / Smoker のすべてで同じ `on` を使う。
- このエントリで支払う報酬は「取り出した個数 × reward」となる。

`item_crafted` のとき。

- `item`：クラフト結果のアイテム種別。
- このエントリで支払う報酬は「取り出した個数 × reward」となる（シフトクリックの複数取り出しに対応）。

`item_enchanted` のとき。

- `item`：エンチャント対象のアイテム種別。
- `enchantment`：エンチャント種別（省略可）。指定時は該当エンチャントを含むエンチャント操作のみマッチ。
- `level_min`：最低レベル（省略可、整数）。指定時は該当エンチャントのレベルがこれ以上の場合のみマッチ。

`item_repaired` のとき。

- `item`：修復対象のアイテム種別。
- `source`：`anvil` または `mending`。省略時は両方にマッチ。

`entity_bred` のとき。

- `entity`：交配で生まれたエンティティ種別。

`entity_tamed` のとき。

- `entity`：手懐けたエンティティ種別。

`entity_sheared` のとき。

- `entity`：毛刈り対象のエンティティ種別。

`item_consumed` のとき。

- `item`：消費したアイテム種別。
- `category`：`food` または `drink`（省略可）。指定時は該当カテゴリのみマッチ。

`villager_traded` のとき。

- `item`：取引で得たアイテム種別。
- このエントリで支払う報酬は「取引した個数 × reward」となる。

`item_brewed` のとき。

- `item`：醸造結果のアイテム種別。
- このエントリで支払う報酬は「出力 slot 数 × reward」となる。

`advancement` のとき。

- `advancement`：advancement キー（`jobs:combat/charged_creeper_sword_kill` のような形式）。

### マッチ順序

`rewards` 配列の上から下に向かってスキャンし、最初にマッチしたエントリで報酬を確定する（first match wins、[ADR-0004](./adr/0004-first-match-wins.md)）。
タグや汎用エントリを上に置くと、それより下の特化エントリが影に隠れる。
起動時にこの「shadow」を検出して警告を出す。

## variety_penalty

単調性ペナルティはジョブごとに設定する。

- `enabled`：有効化フラグ。
- `window`：直近何件のアクションを対象とするか。
- `curve`：直近 `window` 件中の派生キー最多比率に応じた報酬倍率。`up_to` は上限値（その値以下）。曲線の最後のエントリは `up_to: 1.01` のように 1.00 を含むように書く。
- `disclosed_message`：ペナルティが発動したときにプレイヤーへ見せるメッセージ。
- `hide_numbers`：`true` のとき、`disclosed_message` 以外の数値情報を UI に出さない。

派生キーがカテゴリ分けされる仕組みは持たない（[ADR-0006](./adr/0006-builtin-variety-penalty.md)）。
バケットを束ねたい場合は、OR を使って 1 エントリにまとめる。

## anti_automation

自動化対策のフラグ。

- `spawner_origin_kills`：`zero` のとき、スポナー由来 MOB の討伐報酬を 0 にする。
- `unplanted_crop_harvest`：`zero` のとき、プレイヤーが植えていない作物の収穫報酬を 0 にする。
- `recently_placed_break`：直近に置かれた block の破壊報酬を 0 にする（[ADR-0016](./adr/0016-recently-placed-break.md)）。
  - `window_sec`：置かれてからこの秒数以内の破壊を対象とする。デフォルト 3600。
  - 作物ブロック（`Ageable`）は追跡対象外で、`unplanted_crop_harvest` 側で扱う。
- `auto_fed_processing`：Furnace / BlastFurnace / Smoker / BrewingStand で hopper / dispenser 経由の自動投入から得た完成品の報酬を 0 にする（[ADR-0017](./adr/0017-operator-tracking-common.md)）。
  - `operator_ttl_sec`：手動投入の operator を有効とみなす秒数。デフォルト 60。
  - 対象容器はプラグイン側で固定リスト（4 種）を持ち、YAML では列挙しない。
- `villager_repeat_trade`：同 villager × 同 recipe の連続取引に対する cooldown。
  - `cooldown_sec`：デフォルト 60。
  - `scope`：`recipe`（同 villager × 同 recipe）で固定。
- `breed_non_player_breeder`：`zero` のとき、`EntityBreedEvent#getBreeder` が Player でない場合の交配報酬を 0 にする。

`zero` 以外の処理は将来追加する余地として予約しておく。

## グローバル設定（config.yml）

```yaml
specialty_mode:
  reward_non_specialty: 0.0       # 専業外は 0 固定
  show_select_dialog_on_join: true # 未選択プレイヤーのログイン時に選択ダイアログを自動表示するか
  change_policy:
    - within: { event_hours: [0, 24] }
      cooldown: 1h
    - default:
      cooldown: 5d

daily_cap:
  amount: 1000000
  reset_at: "00:00"
  scope: total

persistence:
  type: mysql
  host: ...
  database: jobs
  pool_size: 8
  retention_days: 30

kvs:
  type: memory
```

**reward_non_specialty**：専業外アクションの扱い。
0.0 のとき、報酬支払いも行動ログ書き込みも行わない（[ADR-0002](./adr/0002-non-specialty-actions-discarded.md)）。

**show_select_dialog_on_join**：未選択プレイヤーがログインした直後に選択ダイアログを自動で開くかどうか。
`false` のときはプレイヤーが自発的に `/jobs select` を実行するまで表示しない。
専業未選択でも他プラグインの UI 動線に譲りたい運用（ロビーサーバや、既存の職業案内 UI がある環境）で使う。

**change_policy**：専業変更のクールダウン。
上から評価し、`within` 条件にマッチした最初のポリシーが適用される。
`default` は条件なしのフォールバック。

**daily_cap**：1 日あたりの報酬上限。
`scope: total` は全ジョブ合算、`per_job` は職業別。

**persistence**：MySQL 接続情報。

**kvs**：自動化対策の追跡ストレージ設定。
`memory` のとき in-memory 実装（Caffeine ベース）を使う。
`redis` は Phase 2 の予約（[ADR-0015](./adr/0015-kvs-abstraction.md)）。

## 関連 ADR

- [ADR-0003 match に明示 ID を持たせない](./adr/0003-match-without-explicit-id.md)
- [ADR-0004 first match wins で評価する](./adr/0004-first-match-wins.md)
- [ADR-0005 OR の表現戦略](./adr/0005-or-expression-strategy.md)
- [ADR-0006 単調性ペナルティを内蔵する](./adr/0006-builtin-variety-penalty.md)
- [ADR-0011 自動化対策を内蔵する](./adr/0011-builtin-anti-automation.md)
- [ADR-0015 追跡ストレージを KVS 抽象化する](./adr/0015-kvs-abstraction.md)
- [ADR-0016 recently_placed_break は placer 非依存](./adr/0016-recently-placed-break.md)
- [ADR-0017 投入者追跡を共通化する](./adr/0017-operator-tracking-common.md)
