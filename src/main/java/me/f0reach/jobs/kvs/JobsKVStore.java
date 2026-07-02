package me.f0reach.jobs.kvs;

import java.time.Duration;
import java.util.Optional;

/**
 * 自動化対策の追跡データを保持する KVS の抽象。
 * 詳細は spec/05-persistence.md 「追跡ストレージ (KVS)」を参照。
 *
 * put の TTL は「その key が有効な時間」であり、実装は expireAfterWrite 相当で消す。
 * 呼び出し側は byte[] の直列化を各自で行う。
 */
public interface JobsKVStore {
    void put(String key, byte[] value, Duration ttl);

    Optional<byte[]> get(String key);

    void remove(String key);
}
