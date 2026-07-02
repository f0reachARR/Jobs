# 永続化

行動ログ・専業履歴・日次集計はリレーショナルストアに持ち、自動化対策の追跡データは KVS に持つ。
役割が異なるため 2 系統で分ける。

リレーショナルストア側は Phase 1 の実装として MySQL を使う（[ADR-0009](./adr/0009-mysql-persistence.md)）。
別方言（SQLite など）を後付けで差し込む余地を残すため、アクセス経路はリポジトリインタフェースで抽象化する（[ADR-0018](./adr/0018-repository-interface.md)）。

## リポジトリ層

リレーショナルストアへのアクセスはリポジトリインタフェース越しに限定する。
インタフェースはドメイン操作だけを露出し、SQL は各実装が方言別に丸ごと書く。
共有するのはメソッドシグネチャと DTO 型（`PlayerJobRow`, `ActionLogRow`, `ActionFilter`, `TimeRange` 等）だけで、SQL 文字列・DDL・マイグレーションスクリプトは方言別に独立管理する。

```java
public interface PlayerJobRepository {
  Optional<PlayerJobRow> findCurrent(UUID player);
  void insertSelection(UUID player, String jobId, Instant selectedAt);
  Optional<Instant> lastChangedAt(UUID player);
}

public interface ActionLogRepository {
  void insertBatch(List<ActionLogRow> rows);
  long countActions(UUID player, ActionFilter filter, TimeRange range);
  long sumReward(UUID player, ActionFilter filter, TimeRange range);
  Set<String> distinctKeys(UUID player, ActionFilter filter, TimeRange range);
  int continuousStreakSec(UUID player, ActionFilter filter, TimeRange range);
  int maxUnitPrice(UUID player, ActionFilter filter, TimeRange range);
  int deleteOlderThan(Instant cutoff);
}

public interface DailyRewardTotalRepository {
  long getTotal(UUID player, LocalDate date);
  void addBatch(List<DailyRewardDelta> deltas);
}
```

具象実装は方言別のサブパッケージに置く。

- `persistence.mysql.*`（Phase 1、後述）
- `persistence.sqlite.*`（Phase 2 余地、未実装）

`ActionLogQueryService`（[06-public-api.md](./06-public-api.md)）は `ActionLogRepository` の上位ラッパーとして実装する。
方言抽象化ライブラリ（jOOQ、Hibernate）は使わない。

## MySQL 実装

Phase 1 の具象実装は MySQL 前提で書く。
複数 Paper インスタンス（プロキシ配下）構成への拡張余地を確保する意図が主で、Minecraft サーバ系ホスティングでの導入慣習の高さも合わせて選ぶ理由になる。

### 接続管理

接続プールは HikariCP を使う。
`pool_size` は `config.yml` から取り、デフォルトは 8 とする。
ヘルスチェッククエリは `SELECT 1`。

### テーブル一覧

| テーブル | 用途 |
|---|---|
| `player_job` | プレイヤーの専業選択履歴 |
| `action_log` | 1 アクション 1 行の生ログ |
| `daily_reward_total` | 日次キャップ用の集計 |
| `hourly_aggregate` | 業績指標クエリ用の集計キャッシュ（任意） |

`hourly_aggregate` は最初は作らず、`action_log` への直接 GROUP BY で運用する。
クエリレイテンシが許容範囲を超えた段階で導入する。

### player_job

プレイヤーの専業選択を履歴付きで保持する。
最新行が現在の専業である。

```sql
CREATE TABLE player_job (
  player_uuid     BINARY(16) NOT NULL,
  job_id          VARCHAR(32) NOT NULL,
  selected_at     DATETIME(3) NOT NULL,
  PRIMARY KEY (player_uuid, selected_at),
  INDEX idx_current (player_uuid, selected_at DESC)
) ENGINE=InnoDB;
```

専業変更のクールダウン判定では、`MAX(selected_at)` を見る。
変更履歴はクエスト評価（since: quest_start などの境界）で参照される余地もある。

### action_log

行動ログの中核。

```sql
CREATE TABLE action_log (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  player_uuid     BINARY(16) NOT NULL,
  job_id          VARCHAR(32) NOT NULL,
  action_key      VARCHAR(128) NOT NULL,
  base_reward     INT NOT NULL,
  final_reward    INT NOT NULL,
  rare_hit        BOOLEAN NOT NULL DEFAULT FALSE,
  amount          INT NOT NULL DEFAULT 1,
  occurred_at     DATETIME(3) NOT NULL,
  INDEX idx_player_time (player_uuid, occurred_at),
  INDEX idx_job_time (job_id, occurred_at),
  INDEX idx_player_job_time (player_uuid, job_id, occurred_at DESC)
) ENGINE=InnoDB;
```

**player_uuid**：プレイヤー UUID。`BINARY(16)` に詰める。

**job_id**：マッチしたエントリが属するジョブ。

**action_key**：派生キー（[03-action-detection.md](./03-action-detection.md) を参照）。

**base_reward**：基礎報酬（rare 適用後、Modifier 適用前）。

**final_reward**：Modifier 適用後、Splitter 適用前の最終報酬。

**rare_hit**：rare ボーナスがヒットしたかのフラグ。

**amount**：`item_smelted` で取り出した個数。それ以外は 1。

**occurred_at**：イベント発生時刻（ミリ秒精度）。

書き込みは非同期キュー経由のバッチ INSERT で行う。
1 秒ごとまたは 1000 件ごとに flush する。
クラッシュで失われる最大量は最大バッチ分に限る。

#### パーティション

イベント期間が 21 日と決まっていれば不要だが、長期運用を視野に入れる場合は `occurred_at` で `RANGE PARTITION` を切る。
週単位でパーティションを区切り、古いパーティションを DROP する運用にする。

### daily_reward_total

日次キャップ判定の高速化のための集計テーブル。

```sql
CREATE TABLE daily_reward_total (
  player_uuid     BINARY(16) NOT NULL,
  reward_date     DATE NOT NULL,
  total_reward    BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_uuid, reward_date)
) ENGINE=InnoDB;
```

`action_log` 書き込みと同じバッチで `INSERT ... ON DUPLICATE KEY UPDATE total_reward = total_reward + VALUES(total_reward)` を発行する。

### hourly_aggregate（後付け）

業績指標クエリが頻発する場合の集計キャッシュ。

```sql
CREATE TABLE hourly_aggregate (
  player_uuid     BINARY(16) NOT NULL,
  job_id          VARCHAR(32) NOT NULL,
  bucket_hour     DATETIME NOT NULL,
  action_count    INT NOT NULL,
  distinct_keys   INT NOT NULL,
  total_reward    BIGINT NOT NULL,
  max_unit_price  INT NOT NULL,
  continuous_sec  INT NOT NULL,
  PRIMARY KEY (player_uuid, job_id, bucket_hour),
  INDEX idx_bucket (bucket_hour, job_id)
) ENGINE=InnoDB;
```

毎時 59:30 ごろにバッチで埋める。
クエリ側は「直近の確定済みバケット」を `hourly_aggregate` から、「進行中バケット」を `action_log` から取得して合算する。

### 書き込みのスレッドモデル

`action_log` と `daily_reward_total` への書き込みは、報酬パイプラインの段階 10 から非同期キューに積む。
専用のワーカスレッド 1 本がキューを drain し、バッチで INSERT する。
バッチ送信が完了するまで、キュー内のエントリはオンメモリにある。
プラグイン停止時には flush を待つ。

### 読み込みのスレッドモデル

集計クエリは外部プラグインから呼ばれる。
[06-public-api.md](./06-public-api.md) で定義する `ActionLogQueryService` の各メソッドは、Bukkit main thread からの呼び出しを想定しない。
呼び出し側で非同期で叩く前提とする。

### 行動ログの保持期間

`retention_days` を超えた行は日次バッチで削除する。
イベント期間と等しい 21 日が下限。
ジョブ統計やシーズン振り返り用に保持を伸ばしたい場合は運営判断で `retention_days` を伸ばす。

## SQLite 実装（未実装、将来余地）

Phase 2 の余地として位置付ける。
現時点で実装しない。
複数 Paper インスタンス構成での運用は想定しない。
開発環境（MySQL コンテナを立てずに単一 .db ファイルで統合テストや動作確認を回したい場合）や、単一 Paper インスタンスの小規模運用向けに、後日必要が生じたときに追加する。

追加する際は、リポジトリインタフェースは同じものを実装し、次の点で MySQL 実装と分岐する。

- **DDL**：`BINARY(16)` は `BLOB` へ、`DATETIME(3)` は `TEXT`（ISO8601）か `INTEGER`（epoch millis）へ、`ENGINE=InnoDB` は不要。
- **UPSERT**：`INSERT ... ON DUPLICATE KEY UPDATE` は `INSERT ... ON CONFLICT ... DO UPDATE SET` に置き換える。
- **接続プール**：HikariCP は使わず、単一 write 接続 + WAL モードで運用する（SQLite の並列書き込み制約に合わせる）。
- **パーティション**：SQLite は RANGE PARTITION を持たない。保持期間バッチによる DELETE のみで運用する。

SQL・DDL・マイグレーションスクリプトを MySQL 実装と共有しない方針は [ADR-0018](./adr/0018-repository-interface.md) で決めている。
呼び出し側は方言差を意識せず、リポジトリインタフェースだけを見る。

## 追跡ストレージ（KVS）

自動化対策の判定に使う短寿命データは KVS に持つ（[ADR-0015](./adr/0015-kvs-abstraction.md)）。
MySQL には持たない。
hot path（BlockBreakEvent 直前の get など）で DB 往復を挟まないためである。

### interface

プラグイン内部に 1 枚の interface を切る。

```java
public interface JobsKVStore {
  void put(String key, byte[] value, Duration ttl);
  Optional<byte[]> get(String key);
  void remove(String key);
}
```

呼び出し側は `byte[]` の直列化を各自で担う。
現時点で保存対象は次の 3 種類のみで、いずれも数値かエンティティ ID の組で表現できる。

- `place:<world-uuid>:<x>:<y>:<z>` → `placed_at`（8 バイト、epoch millis）
- `op:<container-kind>:<world-uuid>:<x>:<y>:<z>` → `{ operator_uuid: 16 バイト or null, updated_at: 8 バイト }`
- `trade:<villager-uuid>:<recipe-index>` → `last_traded_at`（8 バイト）

### in-memory 実装（Phase 1）

デフォルトの `InMemoryKVStore` は Caffeine ベースで実装する。
`expireAfterWrite(ttl)` で TTL、`maximumSize` で上限を管理する。

- `maximumSize`：`config.yml` の `kvs.memory.max_entries`（デフォルト 500,000）。
- Caffeine は Paper に shaded で含まれるため、新規依存は増えない。

サーバ再起動でエントリはロストする。
`recently_placed_break` の窓が 1 時間程度であれば、再起動直後の一時的な取りこぼしは実害が小さい。

### Redis 実装（Phase 2）

`RedisKVStore` は Phase 2 で追加する余地を残す。
本仕様の実装対象外だが、interface を先に切ることで呼び出し側の書き換えを不要にする。
Redis を採用するのは、複数 Paper インスタンス構成が現実になったとき（[ADR-0009](./adr/0009-mysql-persistence.md) の long-term vision）に限る。

### 設定

```yaml
kvs:
  type: memory        # or "redis" (Phase 2)
  memory:
    max_entries: 500000
  # redis:
  #   host: 127.0.0.1
  #   port: 6379
  #   password: ...
```

## 関連 ADR

- [ADR-0009 永続化を MySQL とする](./adr/0009-mysql-persistence.md)
- [ADR-0015 追跡ストレージを KVS 抽象化する](./adr/0015-kvs-abstraction.md)
- [ADR-0016 recently_placed_break は placer 非依存](./adr/0016-recently-placed-break.md)
- [ADR-0017 投入者追跡を共通化する](./adr/0017-operator-tracking-common.md)
- [ADR-0018 リポジトリ層をインタフェースで切る](./adr/0018-repository-interface.md)
