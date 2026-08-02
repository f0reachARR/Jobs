package me.f0reach.jobs.kvs;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * KVS の中身を人間が覗く / 掃除するための管理操作。
 * 詳細は spec/adr/0020-kvs-admin-interface.md を参照。
 *
 * <p>{@link JobsKVStore} 本体は hot path の 3 メソッドだけに保ちたいので、列挙系は
 * この interface に分ける。backend が列挙を提供できない場合は
 * {@link JobsKVStore#admin()} が empty を返し、コマンド側が「未対応」を表示する。
 *
 * <p>実装は I/O を伴い得るため、呼び出し側は main thread から直接叩かない。
 */
public interface JobsKVStoreAdmin {

    /**
     * 1 エントリのスナップショット。
     *
     * @param key          KVS の key
     * @param value        値のコピー (呼び出し側が書き換えても store に影響しない)
     * @param remainingTtl 残り有効時間。負にはならない
     */
    record Entry(String key, byte[] value, Duration remainingTtl) {}

    /** 表示用の backend 名 ("memory" など)。 */
    String backendName();

    /** 現在のエントリ数。期限切れだが未回収のものを含み得る概算値。 */
    long size();

    /** エントリ数の上限。上限を持たない backend は -1 を返す。 */
    long maxEntries();

    /**
     * key が prefix で始まるエントリを最大 limit 件返す。期限切れは含めない。
     * 空文字の prefix は全件を意味する。順序は規定しない。
     */
    List<Entry> scan(String keyPrefix, int limit);

    /**
     * key が prefix で始まる有効なエントリの件数。
     * 値をコピーせずに数えるため、集計だけなら {@link #scan} より軽い。
     */
    long count(String keyPrefix);

    /** 1 件を TTL 付きで覗く。未登録・期限切れなら empty。 */
    Optional<Entry> inspect(String key);

    /**
     * key が prefix で始まるエントリを削除し、削除件数を返す。
     * 空文字の prefix は全削除。期限切れのまま残っていたエントリも件数に含める。
     */
    int removeByPrefix(String keyPrefix);
}
