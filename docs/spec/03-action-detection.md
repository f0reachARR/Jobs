# アクション検知

アクション検知は二つのトラックで行う（[ADR-0001](./adr/0001-hybrid-action-detection.md)）。
代表的なアクションは Bukkit イベントから直接拾う（Track A）。
Bukkit イベントだけでは表現しづらい複合条件は、データパック側の advancement に逃がす（Track B）。

## Track A：Bukkit イベントによる native 検知

Paper の標準イベントに直接 listener を張る。
Track A だけで 5 職業の代表アクションをすべて表現できる。

| on | Bukkit イベント | 主な match フィールド | reward の amount 解釈 |
|---|---|---|---|
| `entity_killed` | `EntityDeathEvent`（killer がプレイヤー） | `entity` | 1 アクションごとに 1 件 |
| `block_broken` | `BlockBreakEvent`、TNT 由来は `EntityExplodeEvent` を合成 | `block`、`crop_mature`、`via_tnt` | 1 アクションごとに 1 件 |
| `block_placed` | `BlockPlaceEvent`（Player 発火のみ） | `block` | 1 アクションごとに 1 件 |
| `item_fished` | `PlayerFishEvent`（state は CAUGHT_FISH か CAUGHT_ENTITY） | `item`、`treasure` | 1 アクションごとに 1 件 |
| `item_smelted` | `FurnaceExtractEvent`（Furnace / BlastFurnace / Smoker 共通） | `item` | 取り出した個数 × reward |
| `item_crafted` | `CraftItemEvent`（Player click のみ） | `item` | 取り出した個数 × reward |
| `item_enchanted` | `EnchantItemEvent` | `item`、`enchantment`、`level_min` | 1 アクションごとに 1 件 |
| `item_repaired` | Anvil の `InventoryClickEvent` と `PlayerItemMendEvent` | `item`、`source` | 1 アクションごとに 1 件 |
| `entity_bred` | `EntityBreedEvent`（`getBreeder` が Player） | `entity` | 1 アクションごとに 1 件 |
| `entity_tamed` | `EntityTameEvent` | `entity` | 1 アクションごとに 1 件 |
| `entity_sheared` | `PlayerShearEntityEvent` | `entity` | 1 アクションごとに 1 件 |
| `item_consumed` | `PlayerItemConsumeEvent` | `item`、`category` | 1 アクションごとに 1 件 |
| `villager_traded` | `InventoryClickEvent`（Merchant の result slot） | `item` | 取引個数 × reward |
| `item_brewed` | `BrewEvent` | `item` | 出力 slot 数 × reward |
| `advancement` | `PlayerAdvancementDoneEvent`（Track B） | `advancement` | 1 アクションごとに 1 件 |

`BlockPlaceEvent` は報酬イベントであると同時に、未植え作物判定と `recently_placed_break` のマーキングにも使う。
`InventoryClickEvent` と `InventoryMoveItemEvent` は `auto_fed_processing` の投入者追跡にも使う。

## match の評価

各 `rewards` エントリは派生キーを持つ。
派生キーはマッチ条件から決定的に生成される（[ADR-0003](./adr/0003-match-without-explicit-id.md)）。

| エントリ | 派生キー |
|---|---|
| `on: entity_killed`、`entity: minecraft:zombie` | `kill:minecraft:zombie` |
| `on: entity_killed`、`entity: [zombie, husk]` | `kill:[husk|zombie]`（要素はソート） |
| `on: entity_killed`、`entity: "#minecraft:undead"` | `kill:#minecraft:undead` |
| `on: block_broken`、`block: minecraft:diamond_ore` | `break:minecraft:diamond_ore` |
| `on: block_broken`、`block: minecraft:stone`、`via_tnt: true` | `break_tnt:minecraft:stone` |
| `on: block_placed`、`block: minecraft:stone` | `place:minecraft:stone` |
| `on: item_fished`、`treasure: true` | `fish:treasure` |
| `on: item_smelted`、`item: minecraft:iron_ingot` | `smelt:minecraft:iron_ingot` |
| `on: item_crafted`、`item: minecraft:iron_pickaxe` | `craft:minecraft:iron_pickaxe` |
| `on: item_enchanted`、`item: minecraft:diamond_sword` | `enchant:minecraft:diamond_sword` |
| `on: item_repaired`、`item: minecraft:diamond_pickaxe`、`source: anvil` | `repair_anvil:minecraft:diamond_pickaxe` |
| `on: entity_bred`、`entity: minecraft:cow` | `breed:minecraft:cow` |
| `on: entity_tamed`、`entity: minecraft:wolf` | `tame:minecraft:wolf` |
| `on: entity_sheared`、`entity: minecraft:sheep` | `shear:minecraft:sheep` |
| `on: item_consumed`、`item: minecraft:golden_apple` | `consume:minecraft:golden_apple` |
| `on: villager_traded`、`item: minecraft:emerald` | `trade:minecraft:emerald` |
| `on: item_brewed`、`item: minecraft:potion` | `brew:minecraft:potion` |
| `on: advancement`、`advancement: jobs:combat/x` | `adv:jobs:combat/x` |

派生キーは行動ログの `action_key` カラムと単調性ペナルティのバケット識別子を兼ねる。
OR をリストやタグで指定したエントリは 1 バケットに集約される。
バケットを分けたければ、エントリを分けて宣言する。

## tag の解決

`#<namespace>:<tag-name>` の形式は Paper の `Registry.tags()` 経由で resolve する。
Vanilla の tag だけでなく、サーバ管理者がデータパックで追加した `data/<ns>/tags/...` も透過的に扱える。

resolve は `LifecycleEvents.SERVER_LOAD` 時点に 1 度実行し、結果をキャッシュする。
データパック reload を契機に再 resolve する。

## first match wins とその落とし穴

`rewards` 配列は上から評価する（[ADR-0004](./adr/0004-first-match-wins.md)）。
同じイベントが複数のエントリに該当しても、最初にマッチした 1 エントリだけが採用される。

このため、汎用エントリ（タグ指定や広範な OR リスト）を上に置き、特化エントリを下に置くと、特化側が常に影に隠れる。
起動時に、各エントリのマッチ集合を事前展開した上で、先行エントリが後続エントリのマッチ集合を完全に覆ってしまう場合を「shadow」として検出し、警告ログを出す。

## Track B：データパック advancement

Bukkit イベントでは表現しづらい条件は、データパック内の advancement に投げる。
代表例は次のような場合である。

- 「帯電クリーパーを剣で倒した」
- 「特定の biome で特定アイテムを得た」
- 「特定の NBT を持つ MOB を倒した」

advancement は同梱データパック `data/jobs/advancement/` に配置する。
すべて hidden、親なし、1 criterion で構成する。
プラグインは `PlayerAdvancementDoneEvent` を購読し、namespace が `jobs` の advancement に限定して処理する。
報酬支払いとログ書き込みを終えたあと、`AdvancementProgress.revokeCriteria()` で criterion を取り消し、次の発火に備える。

データパックの配置と有効化は `LifecycleEventManager` の reload hook で扱う。
Track B を使わない最小構成では、`data/jobs/advancement/` を空のままにできる。

## crop_mature と treasure の判定

`crop_mature: true` は Paper の標準 API で済む。
`block.getBlockData() instanceof Ageable` であって `getAge() == getMaximumAge()` のとき真とする。

`treasure: true` は `PlayerFishEvent` の `getCaught()` を見て、内容物が `#minecraft:fishing/treasure` ループテーブル相当のアイテムであるかで判定する。
ループテーブルそのものを引いて確認する API はないため、判定対象アイテム集合をプラグイン内に持つ。
集合は YAML 設定で上書きできる。

## 自動化対策のマーキング

自動化対策の判定は 5 種類に分かれる。
スポナー由来判定と未植え作物判定は既存の仕組みを踏襲し、`recently_placed_break`、`auto_fed_processing`、`villager_repeat_trade` は KVS（[ADR-0015](./adr/0015-kvs-abstraction.md)）に載せる。

### スポナー由来 MOB

`EntityDeathEvent` の時点で `Entity.getEntitySpawnReason()` を確認する。
ジョブの報酬パイプラインは `EntityDeathEvent` で動くため、別途マーキングは不要で、評価時にその場で確認する。

### 未植え作物

`BlockPlaceEvent` でブロックの `PersistentDataContainer` に「植えた」フラグを書き、`BlockBreakEvent` で参照する。
チャンクアンロードや爆発による破壊で `PDC` が消えるリスクは Paper 標準の挙動に従う。
高頻度植えで `PDC` 書き込みのオーバーヘッドが問題になる場合は、`Chunk PDC` に座標集合を持つ方式へ移行する余地を残す。

作物判定は `unplanted_crop_harvest` が担う。
作物ブロックは後述の `recently_placed_break` の追跡対象からは除外する。

### recently_placed_break

placer を問わず、直近に置かれた block の破壊を検知する（[ADR-0016](./adr/0016-recently-placed-break.md)）。

`BlockPlaceEvent`（Ageable 以外の全ブロック）で KVS に書く。

- key：`place:<world-uuid>:<x>:<y>:<z>`
- value：`placed_at`（epoch millis）
- ttl：`window_sec`

`BlockBreakEvent` の段階 3 直前に `get` する。
残っていれば `via_recently_placed=true` を立てて 0 確定する。

### auto_fed_processing

Furnace 系（Furnace / BlastFurnace / Smoker）と BrewingStand の投入者を追跡する（[ADR-0017](./adr/0017-operator-tracking-common.md)）。

`InventoryClickEvent` で Player が対象容器の入力 slot に材料を置いた場合、KVS を上書き。

- key：`op:<container-kind>:<world-uuid>:<x>:<y>:<z>`
- value：`{ operator_uuid: player.uuid, updated_at: now }`
- ttl：`operator_ttl_sec`

`InventoryMoveItemEvent` で hopper / dispenser が同 slot に材料を移した場合、`operator_uuid: null` で上書き。

`FurnaceExtractEvent` と `BrewEvent` の段階 3 直前に `get` する。
`operator_uuid` が null または未登録なら `via_auto_fed=true` を立てて 0 確定する。
非 null なら extractor 本人との一致は問わず、通す。

対象容器は 4 種で内蔵固定とし、YAML 側では列挙しない。

対象 slot は入力 slot のみとする。
Furnace 系の燃料 slot と BrewingStand の blaze powder slot は追跡対象外で、石炭のホッパー供給のような正当な運用を巻き込まない。

### villager_repeat_trade

同 villager × 同 recipe の連続取引に cooldown を掛ける。

`InventoryClickEvent`（Merchant の result slot）で取引が成立した瞬間、Villager UUID と recipe index を key にして KVS に `last_traded_at` を書く。

- key：`trade:<villager-uuid>:<recipe-index>`
- value：`last_traded_at`（epoch millis）
- ttl：`cooldown_sec`

次回の同 key の取引時、KVS にエントリが残っていれば `via_repeat_trade=true` を立てて 0 確定する。

### breed_non_player_breeder

`EntityBreedEvent#getBreeder` が Player でない場合、`via_non_player_breeder=true` を立てて 0 確定する。
Villager breeder 経由の交配など、Player を介さない自動交配を弾く。
追跡データは不要で、イベント発火時にその場で確認する。

### TNT 起爆者の追跡

TNT 由来の `block_broken` を扱うため、TNT を起爆した Player を追跡する。

- `PlayerInteractEvent`（flint & steel を TNT ブロックに使用）で近傍の TNT primed 化直前を検出。
- `BlockPlaceEvent`（TNT block 設置）と `BlockIgniteEvent` で TNT の起爆経路を追う。
- TNT が primed になった時点で、`TNTPrimed` エンティティの `PersistentDataContainer` に起爆者 UUID を書く。
- `EntityExplodeEvent` で対象 block リストを取り、`TNTPrimed` の PDC から起爆者を引く。
- 対象 block を「合成 `BlockBreakEvent`」として同じパイプラインに流し、`via_tnt=true` フラグを立てる。

起爆者追跡は `TNTPrimed` エンティティが短命で PDC のライフサイクルに載る（爆発と同時に消滅）ため、KVS には載せない。

### 判定の合流

これらの判定はすべて報酬パイプラインの段階 3（自動化対策）で評価される。
段階 3 で 0 確定したアクションは、以降の Modifier や Splitter では 0 以外に戻らない。
詳細は [04-reward-pipeline.md](./04-reward-pipeline.md) を参照。

## advancement 経路と native 経路の合流

両経路は同じ報酬パイプラインに合流する。
合流地点は「派生キーとマッチした rewards エントリが決定した直後」であり、それ以降の処理は共通である。
パイプラインの詳細は [04-reward-pipeline.md](./04-reward-pipeline.md) を参照。

## 関連 ADR

- [ADR-0001 ハイブリッド検知](./adr/0001-hybrid-action-detection.md)
- [ADR-0003 match に明示 ID を持たせない](./adr/0003-match-without-explicit-id.md)
- [ADR-0004 first match wins で評価する](./adr/0004-first-match-wins.md)
- [ADR-0005 OR の表現戦略](./adr/0005-or-expression-strategy.md)
- [ADR-0011 自動化対策を内蔵する](./adr/0011-builtin-anti-automation.md)
- [ADR-0015 追跡ストレージを KVS 抽象化する](./adr/0015-kvs-abstraction.md)
- [ADR-0016 recently_placed_break は placer 非依存](./adr/0016-recently-placed-break.md)
- [ADR-0017 投入者追跡を共通化する](./adr/0017-operator-tracking-common.md)
