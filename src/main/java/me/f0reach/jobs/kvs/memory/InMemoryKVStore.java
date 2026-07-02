package me.f0reach.jobs.kvs.memory;

import me.f0reach.jobs.kvs.JobsKVStore;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * デフォルトの KVS 実装。
 *
 * spec 05 では Caffeine ベースを想定するが、Paper の compile classpath には
 * Caffeine が露出しないため、Phase 3 では JDK 標準の {@code ConcurrentHashMap}
 * ベースで実装する（挙動は同等：expireAfterWrite 相当を自前で持つ）。
 *
 * key 数が {@code maxEntries} を超えたら、書き込み時にランダムな古いエントリを
 * evict する。KVS の用途は「短寿命の追跡データ」で厳密な LRU は不要。
 */
public final class InMemoryKVStore implements JobsKVStore {

    private record Entry(byte[] value, long expiresAtNanos) {
        boolean expired(long nowNanos) {
            return nowNanos >= expiresAtNanos;
        }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final long maxEntries;

    public InMemoryKVStore(long maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be > 0");
        this.maxEntries = maxEntries;
    }

    @Override
    public void put(String key, byte[] value, Duration ttl) {
        long expiresAt = System.nanoTime() + ttl.toNanos();
        store.put(key, new Entry(value.clone(), expiresAt));
        if (store.size() > maxEntries) {
            evictExpiredOrRandom();
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Entry entry = store.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expired(System.nanoTime())) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value().clone());
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    /** サイズ超過時、既に expired したエントリを優先的に落とし、なければ 1 件だけ落とす。 */
    private void evictExpiredOrRandom() {
        long now = System.nanoTime();
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        boolean anyExpiredRemoved = false;
        while (it.hasNext()) {
            Map.Entry<String, Entry> entry = it.next();
            if (entry.getValue().expired(now)) {
                it.remove();
                anyExpiredRemoved = true;
            }
        }
        if (!anyExpiredRemoved && store.size() > maxEntries) {
            // それでも溢れているなら任意の 1 件を落とす（KVS の用途上、厳密な LRU 不要）
            Iterator<String> keys = store.keySet().iterator();
            if (keys.hasNext()) {
                store.remove(keys.next());
            }
        }
    }

    /** テスト用。全 entry を捨てる。 */
    public void clear() {
        store.clear();
    }

    /** テスト用のサイズ。 */
    public int size() {
        return store.size();
    }
}
