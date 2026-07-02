# ADR-0017 投入者追跡を Furnace 系と BrewingStand で共通化する

## ステータス

受け入れ

## 背景

BrewingStand と Furnace 系（Furnace / BlastFurnace / Smoker）は、いずれも外部から材料を投入して完成品を取り出す装置である。
両者は hopper / dispenser による自動投入で稼働を完全自動化できる。

投入が自動化されている場合、たとえ最終的な取り出しがプレイヤー操作でも、その報酬を払うと自動農場の稼ぎ手段になる。

初期の検討では BrewingStand のみを対象に `brewing_by_dispenser: zero` として個別対策を持つ案があった。
しかし Furnace 側にも同じパターンの自動化がある。

`FurnaceExtractEvent` はプレイヤー発火だが、投入は hopper で自動化できる。
プレイヤーが「自動供給された結果」を取り出すだけで報酬を稼げる構図は BrewingStand と同じである。

投入者追跡の仕組みは両装置で本質的に同じで、対象容器の種別を切り替えるだけで動く。
別々に実装する動機はなく、共通の処理として書くのが自然である。

## 決定

`anti_automation.auto_fed_processing` を採用する。
対象容器は Furnace / BlastFurnace / Smoker / BrewingStand の 4 種を内蔵固定とする。

```yaml
anti_automation:
  auto_fed_processing:
    operator_ttl_sec: 60
```

追跡データは KVS（[ADR-0015](./0015-kvs-abstraction.md)）に持つ。

- key：`op:<container-kind>:<world-uuid>:<x>:<y>:<z>`
- value：`{ operator_uuid: UUID | null, updated_at: epoch millis }`
- ttl：`operator_ttl_sec`

書き込みは 2 系統。

- `InventoryClickEvent` で Player が対象容器の入力 slot に材料を置いた場合、`{ operator_uuid: player.uuid, updated_at: now }` を書く。
- `InventoryMoveItemEvent` で hopper / dispenser が対象容器の入力 slot に材料を移した場合、`{ operator_uuid: null, updated_at: now }` を書く。

読み出しは `FurnaceExtractEvent` と `BrewEvent` の直前に行う。
`operator_uuid` が null または未登録なら報酬 0 確定。
非 null なら extractor 本人と一致するかは問わず、報酬を通す。

対象は入力 slot に限定する。
Furnace 系の燃料 slot と BrewingStand の blaze powder slot は追跡対象外とする。
石炭のホッパー供給のような正当な運用を巻き込まないためである。

対象容器の一覧を YAML で列挙する案は採らない。
運用者が誤って空リストにして防御を無効化する事故を避け、プラグイン側が固定リストを持つ。
将来 Crafter などの追加対象が出た場合はプラグインバージョンで対応する。

`operator_ttl_sec` のデフォルトは 60 とする。
BrewingStand の醸造 1 サイクルは 20 秒、Furnace の精錬 1 サイクルは 10 秒程度で、TTL 60 秒は手動投入から取り出しまでの現実的な時間を余裕を持って包含する。

## 結果

- BrewingStand と Furnace 系で同じ判定ロジックが働く。
- 手動投入と自動投入が混在する運用（燃料は自動供給、材料は手動投入）は正常に払われる。
- 手動投入したあと別プレイヤーが取り出す協力プレイは正常に払われる。extractor と operator の一致は求めない。
- 対象容器のリストが内蔵固定のため、運用者が意図せず空リストにして防御を無効化する事故を避けられる。
- Crafter や新種の container が追加された場合はプラグイン側の対応が必要になる。

## 選択しなかった代替案

BrewingStand のみを対象にする案は、Furnace 系に同じ抜け穴を残す。
両装置の投入自動化パターンは本質的に同じで、同時に対策しないと片方だけが抜け道になる。

対象容器を YAML で列挙可能にする案は、運用者が意図せず空リストにするリスクがある。
新種容器の追加頻度は低く、プラグインバージョンでの追随で足りる。

extractor と operator の一致を求める案は、手動投入したあと別プレイヤーが取り出す正当な協力プレイを巻き込む。
手動投入というプレイヤーの関与が担保されていれば、取り出し者が誰でも自動化ではないと判断できる。
