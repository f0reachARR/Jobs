# ADR-0001 Bukkit イベントと advancement のハイブリッド検知

## ステータス

受け入れ

## 背景

ジョブのアクション検知をどの仕組みで行うかには、いくつかの候補があった。

第一に、すべてを Bukkit イベントで拾う案。
`BlockBreakEvent`、`EntityDeathEvent`、`PlayerFishEvent`、`FurnaceExtractEvent` のような標準イベントを listener で受け、match 条件は YAML 内で表現する。
単純な条件（エンティティ種別、ブロック種別）は素直に書けるが、「帯電クリーパーを剣で倒した」のような複合条件を表現するには、自前で predicate エンジンを実装する必要がある。

第二に、すべてを advancement 経由で拾う案。
Minecraft の advancement criterion は predicate 表現力が高く、条件は Mojang の実装に乗せられる。
ただし「ゾンビを倒すと 5G」程度の単純な検知でも、検知用 advancement を 1 件ずつデータパックに書く必要があり、YAML の rewards 配列とデータパックの両方を編集することになる。
さらに、advancement を繰り返し発火させるには `revokeCriteria` で毎回取り消す運用が必要で、ファイル数が増える。

第三に、両者を場合分けで使う案。
代表的なアクションは Bukkit イベントで直接拾い、複雑な条件のみ advancement に逃がす。

## 決定

第三案を採用する。

`on:` フィールドが `entity_killed`、`block_broken`、`item_fished`、`item_smelted` のいずれかなら native 検知（Track A）で動く。
`on: advancement` のとき、`PlayerAdvancementDoneEvent` をトリガに動き、データパック側に advancement 定義を置く（Track B）。

両経路は match 確定後の報酬パイプラインで合流する。

## 結果

- Track A で 5 職業の代表アクションがすべて表現できる。
  Track B は当面不要、必要が出た段階で追加する。
- 自前 predicate エンジンを実装せず済む。
  複雑条件は Minecraft の predicate JSON で書く。
- YAML の rewards 配列を見れば、サーバ管理者がジョブの全体像を把握できる。
  Track B 利用時のみ、データパックを併せて読む必要が出る。

## 選択しなかった代替案

「全部 Bukkit イベント案」は、predicate エンジンの自前実装コストが高く、Minecraft 側の predicate JSON との表現力差を埋められない。
「全部 advancement 案」は、YAML を介さずデータパック編集に集約する形になり、運営の編集体験が悪化する。
