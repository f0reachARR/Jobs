package me.f0reach.jobs.kvs.memory;

import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.JobsKVStoreAdmin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
public final class InMemoryKVStore implements JobsKVStore, JobsKVStoreAdmin {

    /** 内部保持用。{@link JobsKVStoreAdmin.Entry} (公開する snapshot) とは別物。 */
    private record StoredEntry(byte[] value, long expiresAtNanos) {
        boolean expired(long nowNanos) {
            return nowNanos >= expiresAtNanos;
        }

        /** 残り TTL。既に切れていれば ZERO。 */
        Duration remaining(long nowNanos) {
            long left = expiresAtNanos - nowNanos;
            return left <= 0 ? Duration.ZERO : Duration.ofNanos(left);
        }
    }

    private final ConcurrentHashMap<String, StoredEntry> store = new ConcurrentHashMap<>();
    private final long maxEntries;

    public InMemoryKVStore(long maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be > 0");
        this.maxEntries = maxEntries;
    }

    @Override
    public void put(String key, byte[] value, Duration ttl) {
        long expiresAt = System.nanoTime() + ttl.toNanos();
        store.put(key, new StoredEntry(value.clone(), expiresAt));
        if (store.size() > maxEntries) {
            evictExpiredOrRandom();
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        StoredEntry entry = store.get(key);
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
        Iterator<Map.Entry<String, StoredEntry>> it = store.entrySet().iterator();
        boolean anyExpiredRemoved = false;
        while (it.hasNext()) {
            Map.Entry<String, StoredEntry> entry = it.next();
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

    /** 全 entry を捨てる。 */
    public void clear() {
        store.clear();
    }

    // --- JobsKVStoreAdmin ---

    @Override
    public Optional<JobsKVStoreAdmin> admin() {
        return Optional.of(this);
    }

    @Override
    public String backendName() {
        return "memory";
    }

    @Override
    public long size() {
        return store.size();
    }

    @Override
    public long maxEntries() {
        return maxEntries;
    }

    @Override
    public List<Entry> scan(String keyPrefix, int limit) {
        if (limit <= 0) return List.of();
        long now = System.nanoTime();
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, StoredEntry> e : store.entrySet()) {
            if (!e.getKey().startsWith(keyPrefix)) continue;
            StoredEntry stored = e.getValue();
            if (stored.expired(now)) continue;
            out.add(toEntry(e.getKey(), stored, now));
            if (out.size() >= limit) break;
        }
        return out;
    }

    @Override
    public long count(String keyPrefix) {
        long now = System.nanoTime();
        long n = 0;
        for (Map.Entry<String, StoredEntry> e : store.entrySet()) {
            if (e.getKey().startsWith(keyPrefix) && !e.getValue().expired(now)) n++;
        }
        return n;
    }

    @Override
    public Optional<Entry> inspect(String key) {
        long now = System.nanoTime();
        StoredEntry stored = store.get(key);
        if (stored == null || stored.expired(now)) return Optional.empty();
        return Optional.of(toEntry(key, stored, now));
    }

    @Override
    public int removeByPrefix(String keyPrefix) {
        // 期限切れのまま残っていた entry も「消えた」件数に含める。掃除としてはそれで正しい。
        int removed = 0;
        Iterator<Map.Entry<String, StoredEntry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey().startsWith(keyPrefix)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    private static Entry toEntry(String key, StoredEntry stored, long nowNanos) {
        return new Entry(key, stored.value().clone(), stored.remaining(nowNanos));
    }
}
