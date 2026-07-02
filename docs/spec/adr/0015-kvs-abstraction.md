# ADR-0015 自動化対策の追跡ストレージを KVS 抽象化する

## ステータス

受け入れ

## 背景

自動化対策の判定は、直近に起きた事象の記録に依存する。
既存の未植え作物追跡は Block PDC で「Player が植えた」フラグを書き、`BlockBreakEvent` で参照する形になっている（[ADR-0011](./0011-builtin-anti-automation.md)）。

新たに追加する自動化対策は次の 3 種類の追跡を必要とする。

- `recently_placed_break`：直近に置かれた block を位置キーで追跡する。
- `auto_fed_processing`：Furnace 系と BrewingStand の投入者を位置キーで追跡する。
- `villager_repeat_trade`：Villager の recipe ごとの最終取引時刻をエンティティキーで追跡する。

これらを PDC で実装する場合の懸念は 3 点ある。

第一に、書き込みコストが高い。
`recently_placed_break` は BlockPlaceEvent ごとに Block PDC に書き込むことになる。
既存の未植え作物は Ageable ブロックに限定されていたが、こちらは全ブロックが対象で頻度が桁違いに大きい。

第二に、TTL の管理が煩雑になる。
PDC には TTL の概念がない。
書き込み時刻を値として持って読み出し時に比較する運用は書けるが、能動的な期限切れ回収を自前で書く必要がある。

第三に、複数 Paper インスタンス構成を将来の余地として残す観点（[ADR-0009](./0009-mysql-persistence.md)）で、位置キーで共有できないストレージは将来のボトルネックになる。
PDC は chunk のライフサイクルに縛られ、他インスタンスからは見えない。

一方、Redis のような外部 KVS を最初から必須にする案も過剰である。
現時点の対象は単一 Paper インスタンスであり、Redis の外部依存を導入する追加コストが正当化されない。

## 決定

追跡ストレージを内部 interface `JobsKVStore` で抽象化する。

```java
public interface JobsKVStore {
  void put(String key, byte[] value, Duration ttl);
  Optional<byte[]> get(String key);
  void remove(String key);
}
```

Phase 1 では in-memory 実装 `InMemoryKVStore` のみを提供する。
Caffeine の `expireAfterWrite` で TTL、`maximumSize` で上限を管理する。
Paper に shaded で含まれる Caffeine を使うため新規依存は増えない。

Phase 2 として Redis 実装 `RedisKVStore` を後付けする余地を残す。
実装は本 ADR の対象外だが、interface を最初から切っておくことで、Redis を差し込むときに呼び出し側を書き換えずに済む。

KVS の設定は `config.yml` の `kvs` セクションに置く。

```yaml
kvs:
  type: memory
```

Phase 2 で `type: redis` と接続情報を追加する。

## 結果

- 追跡ストレージが 1 種類の interface に集約される。将来的に既存の Block PDC 依存箇所（未植え作物追跡）を同じ interface に寄せる余地も残る。
- Redis 実装は future work として明確に区切られる。
- Caffeine の TTL に頼れるため、能動的な期限切れ回収コードは持たない。
- サーバ再起動で in-memory の追跡はロストする。
  窓の短い対策では実害が小さいが、`recently_placed_break` の窓を長く取る運用では再起動直後に一時的な取りこぼしが出る点は許容する。

## 選択しなかった代替案

PDC 継続案は書き込みコストと TTL 管理の両面で不利であり、`recently_placed_break` の対象を全ブロックに広げる方針とは合わない。

Redis 必須案は現時点の運用に対して過剰である。
単一 Paper インスタンスで Redis の外部依存を持つのは正当化しづらい。
将来 cross-server が必要になったときに Phase 2 として導入する。

MySQL に追跡テーブルを持たせる案は、hot path（BlockBreakEvent 直前の get）で DB 往復が入る点で不適。
同期実行ではメイン thread を止め、非同期実行ではイベント判定に間に合わない。
