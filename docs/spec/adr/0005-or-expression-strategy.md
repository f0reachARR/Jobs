# ADR-0005 OR 条件はリスト、タグ、データパックで分担する

## ステータス

受け入れ

## 背景

「ゾンビかハスクかドラウンドを倒したら 5G」のような OR 条件をどう表現するかには複数案がある。

第一に、YAML 内に独自の OR 構文を導入する案。

```yaml
- on: entity_killed
  match:
    any_of:
      - entity: minecraft:zombie
      - entity: minecraft:husk
```

これは表現力が高いが、parser を自前で実装する負担と、複雑な条件への拡張要求（AND、NOT、ネスト）に引っ張られるリスクがある。

第二に、match フィールド自体にリストを許す案。

```yaml
- on: entity_killed
  entity: [minecraft:zombie, minecraft:husk, minecraft:drowned]
  reward: 3
```

第三に、Vanilla の registry tag に委譲する案。

```yaml
- on: entity_killed
  entity: "#minecraft:undead"
  reward: 4
```

第四に、複雑な OR は Track B（advancement）に逃がす案。
Minecraft の advancement criterion は requirements 配列で OR を、predicate ファイルで複合条件を表現できる。

## 決定

OR の表現は次の三層で分担する。

- 単純な選択肢の OR：match フィールドにリストを書く（第二案）。
- ドメイン単位の OR：Vanilla または独自データパックの tag を `#namespace:tag` で参照する（第三案）。
- 多フィールドにまたがる複合 OR、ネストした AND/OR：データパックの advancement と predicate で書く（第四案）。

独自の YAML OR 構文は導入しない。

## 結果

- YAML のパーサを単純に保てる。
  match フィールドのリスト受け入れと、`#tag` 文字列の resolve だけが実装範囲。
- 単純なケースは YAML だけで完結し、複雑なケースは Minecraft 標準の表現を借りる。
- リスト OR とタグ OR は派生キーレベルで 1 バケットに集約される（[ADR-0003](./0003-match-without-explicit-id.md) を参照）。
  バケットを分けたい場合はエントリを分けて宣言する。

## 選択しなかった代替案

独自 OR 構文は、将来の拡張要求に引きずられて parser が肥大化するリスクがある。
表現したい OR の大半は、リストか tag で済む。
残りの少数派は advancement に逃がす方が、Minecraft の意味論と整合する。
