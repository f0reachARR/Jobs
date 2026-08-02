# ADR-0020 KVS の管理操作を別インタフェースに分離する

## ステータス

受け入れ

## 背景

[ADR-0015](./0015-kvs-abstraction.md) で自動化対策の追跡ストレージを `JobsKVStore` として抽象化し、`put` / `get` / `remove` の 3 メソッドに固定した。
これは hot path（`BlockBreakEvent` 直前の get など）だけを見た切り方で、追跡データを外から観測する手段を持たない。

運用に入ると、この「見えなさ」が問題になる場面がある。

第一に、自動化対策の誤検知の切り分けができない。
プレイヤーから「ブロックを壊したのに報酬が入らない」と申告されたとき、`recently_placed_break` の `place:` エントリが残っているのか、それとも別の理由（専業外、日次キャップ）なのかを区別する手段がない。
`/jobs admin actions` は行動ログを見せるが、報酬 0 で捨てられたアクションは `action_log` に残らない（[ADR-0002](./0002-non-specialty-actions-discarded.md)）ため、ログ側からも追えない。

第二に、誤検知が確定しても解除できない。
TTL が切れるのを待つしかなく、`recently_placed_break` の窓を 1 時間に設定していれば 1 時間待たせることになる。

第三に、エントリ数の見積もりができない。
`kvs.memory.max_entries`（デフォルト 500,000）を超えると書き込み時に evict が走るが、実際の使用量が上限に対してどの程度かを知る術がない。
上限に張り付いていれば追跡が取りこぼされている可能性があるが、それに気づけない。

これらを解決するには列挙（scan）と件数取得が要る。
一方で、それらを `JobsKVStore` 本体に足すと ADR-0015 が意図した「hot path に必要な最小限」という性質が崩れる。
`recently_placed_break` は `BlockPlaceEvent` ごとに put するため呼び出し頻度が高く、この interface を実装するコストは低く保ちたい。

また、列挙は backend によって性質が大きく異なる。
in-memory の `ConcurrentHashMap` では単なる走査だが、Redis では `SCAN` のカーソル反復になり、レイテンシも桁が違う。
将来 Redis 実装を差し込むとき（ADR-0015 の Phase 2）、構成によっては `SCAN` を許可しない運用もあり得る。

## 決定

管理操作を `JobsKVStoreAdmin` として別インタフェースに切り、`JobsKVStore` からは optional に露出する。

```java
public interface JobsKVStoreAdmin {
  record Entry(String key, byte[] value, Duration remainingTtl) {}

  String backendName();
  long size();
  long maxEntries();
  List<Entry> scan(String keyPrefix, int limit);
  long count(String keyPrefix);
  Optional<Entry> inspect(String key);
  int removeByPrefix(String keyPrefix);
}

public interface JobsKVStore {
  // ... put / get / remove は変更しない
  default Optional<JobsKVStoreAdmin> admin() { return Optional.empty(); }
}
```

`InMemoryKVStore` は両方を実装し、`admin()` で自身を返す。
列挙を提供できない backend は `admin()` を override せず、コマンド側が「この backend は管理操作に対応していません」を表示して終わる。

この interface を使う `/jobs admin kvs` は、閲覧（`stats` / `list` / `get` / `block`）と削除（`remove` / `clear`）のみを提供する。
任意の値を書き込む口は作らない（[08-permissions.md](../08-permissions.md)）。

呼び出しはすべて非同期プール経由とし、メインスレッドには描画だけを戻す。
in-memory 実装では過剰だが、Redis 実装に差し替えたときにネットワーク I/O がメインスレッドに乗るのを構造的に防ぐ。

## 結果

- `JobsKVStore` は 3 メソッドのままで、hot path 側の実装コストは変わらない。
- 誤検知の切り分けが「対象ブロックの追跡エントリを見る → 消す」の 2 手で完結する。
- `scan` を持たない backend でも degrade して動く。管理コマンドが使えないだけで、追跡そのものは動く。
- `InMemoryKVStore` がテスト用として持っていた `clear()` / `size()` が、この interface 上の正式な操作になる。
- 列挙のコストは backend 任せになる。in-memory では上限 500,000 件の全走査が最悪ケースだが、管理コマンドの実行頻度から許容する。

## 選択しなかった代替案

`JobsKVStore` に直接足す案は、ADR-0015 が定めた「hot path に必要な最小限」を崩す。
将来 backend を追加するとき、追跡を書くだけなら不要な列挙まで実装を強いることになる。

管理コマンドから `InMemoryKVStore` に直接キャストする案は、interface を切った意味を失わせる。
Redis 実装を足した時点でコマンド側の書き換えが必要になり、ADR-0015 が避けたかった状況そのものになる。

追跡データを MySQL に二重書きして管理用に参照する案は、hot path に DB 書き込みを足すことになり ADR-0015 の判断と正面から衝突する。
