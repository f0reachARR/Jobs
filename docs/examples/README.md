# Job YAML 例集

このディレクトリは、[../spec/02-yaml-schema.md](../spec/02-yaml-schema.md) と
[../spec/03-action-detection.md](../spec/03-action-detection.md) で規定されている
Job YAML の書き方を、より広い Item / Mob カバレッジで示した「参考例」置き場である。

`src/main/resources/jobs/` にある同梱サンプル
([../../src/main/resources/jobs/combat.yml](../../src/main/resources/jobs/combat.yml) など)
は Job プラグインが初回起動時に自動配置する「default 職業」で、意図的に短く保ってある。
一方このディレクトリの YAML は、Minecraft 1.21.11 の Item / Mob / registry tag をより網羅的に
使い、各 `on:` の match フィールド (single / list / tag / rare / range) や `variety_penalty` /
`anti_automation` の書き分けを参考にしてもらうためのもの。

## 使い方

そのままサーバに投入したい場合、対応する YAML を
`plugins/Jobs/jobs/` にコピーして `id:` を重複させずに置き、`/jobs reload` を実行する。
複数の職業を同居させる場合は `id:` がユニークになることだけ確認する。

## 収録している例

| ファイル | 主に扱う `on:` | ねらい |
|---|---|---|
| [hunter.yml](hunter.yml) | `entity_killed` | tag と OR list を組み合わせた広めの討伐職。boss / raid / nether / end mob を包括する |
| [miner_pro.yml](miner_pro.yml) | `block_broken` | 全鉱石 (deepslate / nether / end variant 含む) + `via_tnt` 別バケットの例 |
| [farmer_pro.yml](farmer_pro.yml) | `block_broken`, `entity_bred` | `crop_mature` を使う作物と、非 Ageable 作物 (nether_wart / sugar_cane / bamboo など) の書き分け |
| [rancher.yml](rancher.yml) | `entity_bred`, `entity_tamed`, `entity_sheared` | 家畜・ペット全般。tag と個別指定の混在 |
| [builder.yml](builder.yml) | `block_placed` | 建築特化。木材・石材・銅・ガラス系を tag / list で拾う |
| [fisher.yml](fisher.yml) | `item_fished` | `treasure` を先頭に置いた first match wins の例、通常魚は個別 |
| [smith.yml](smith.yml) | `item_crafted`, `item_enchanted`, `item_repaired` | 装備クラフト + 付呪 (`level_min` 使用) + 修復 (`source` 別バケット) |
| [chef.yml](chef.yml) | `item_smelted`, `item_crafted`, `item_consumed` | 食料生産と食事。`category: food` / `drink` の書き分け |
| [alchemist.yml](alchemist.yml) | `item_brewed` | potion base type ごとの別バケット、splash / lingering の書き分け |
| [trader.yml](trader.yml) | `villager_traded` | エメラルド経路と稀少取引 (enchanted_book / diamond / netherite など) の分離 |

## 設計メモ

- **first match wins に注意する** — 例えば `hunter.yml` は先頭で個別 mob を高額報酬、下段で
  `#minecraft:undead` を低額拾いに置くことで、影に隠れないよう並べている。
- **派生キー (bucket) の分け方** — `variety_penalty` を効かせたい粒度に合わせて、`entity:` を
  個別で書くか OR list で 1 バケットにまとめるかを選ぶ。同じアクションでも書き方でバケット
  分割が変わる。詳細は [../spec/03-action-detection.md](../spec/03-action-detection.md) の
  「派生キー」表を参照。
- **`anti_automation` は per-job で明示的に off にもできる** — グローバルの default を
  ジョブ単位で打ち消したい場合は `spawner_origin_kills: off` のように書く。
- **MiniMessage 装飾** — `rare.announce` は Bedrock 側で strip されるので、装飾なしでも意味が
  通る文にする。
