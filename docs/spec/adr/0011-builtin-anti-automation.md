# ADR-0011 自動化対策を Job プラグインに内蔵する

## ステータス

受け入れ

## 背景

イベントルールブックは自動化対策として次の二つを定めている。

- スポナー由来 MOB の討伐は報酬 0
- プレイヤーが植えていない作物の収穫は報酬 0

これらを実装する場所には選択肢がある。

第一に、Job プラグインに内蔵する案。

第二に、拡張 Modifier として外部プラグインで実装する案。

第二案は責務分離としては綺麗だが、判定のために `EntityDeathEvent` のスポナー由来フラグや `BlockPlaceEvent` のプレイヤー植え記録が必要であり、これらは Job プラグインの既存 listener と密接にかかわる。
さらに、プレイヤーが植えた作物の追跡には `PersistentDataContainer` を継続的に書き続ける必要があり、外部プラグインで実装すると Job プラグインと並行して同種の listener を持つことになる。

加えて、自動化対策は「Modifier 後のスケーリング」ではなく「マッチ後すぐの 0 確定」として動くべき性質を持つ。
報酬パイプラインの早い段階で判定したく、Modifier chain よりも前段に置くのが自然である。

Minecraft の advancement criterion は「スポナー由来かどうか」を表現する標準 predicate を持たない。
このため Track B（advancement 経由）でも判定できず、プラグイン側の listener で扱うしかない。

## 決定

第一案を採用する。

自動化対策は Job プラグインに内蔵する。
ジョブごとの YAML で `anti_automation` セクションを有効化する。

- `spawner_origin_kills: zero`：`EntityDeathEvent` で `Entity.getEntitySpawnReason() == SPAWNER` を確認し、該当時に報酬を 0 に固定する。
- `unplanted_crop_harvest: zero`：`BlockPlaceEvent` でブロックの `PersistentDataContainer` に「植えた」フラグを書き、`BlockBreakEvent` で参照する。

両判定は報酬パイプラインの段階 3（自動化対策）で行う。
段階 3 で 0 確定したアクションは、以降の Modifier や Splitter で 0 以外には戻らない。

## 結果

- 自動化対策の実装が Job プラグイン内に閉じる。
- スポナー由来 MOB の検出は Paper 標準 API で完結する。
- プレイヤー植え追跡は `BlockPlaceEvent` ごとの `PersistentDataContainer` 書き込みコストを持つ。
  高頻度植えで負荷が問題になる場合、`Chunk PDC` に座標集合を持つ実装に切り替える余地を残す。
- 自動化対策で 0 確定したアクションも行動ログには記録する。
  運営が「自動化アタックの兆候」を観察するための情報源として残す。

## 選択しなかった代替案

外部プラグイン実装案は、listener の二重実装と判定タイミングのずれを生む。
自動化対策は Job プラグインの中核に近い処理であり、内蔵が自然である。
