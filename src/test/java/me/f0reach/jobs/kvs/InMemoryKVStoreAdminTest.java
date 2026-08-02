package me.f0reach.jobs.kvs;

import me.f0reach.jobs.kvs.memory.InMemoryKVStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JobsKVStoreAdmin} の in-memory 実装。/jobs admin kvs が依存する挙動を固定する。
 */
class InMemoryKVStoreAdminTest {

    private static final Duration LONG_TTL = Duration.ofMinutes(1);

    private JobsKVStoreAdmin adminOf(InMemoryKVStore store) {
        Optional<JobsKVStoreAdmin> admin = store.admin();
        assertTrue(admin.isPresent(), "InMemoryKVStore must expose an admin view");
        return admin.get();
    }

    @Test
    void backendMetadataIsExposed() {
        InMemoryKVStore store = new InMemoryKVStore(42);
        JobsKVStoreAdmin admin = adminOf(store);
        assertEquals("memory", admin.backendName());
        assertEquals(42, admin.maxEntries());
    }

    @Test
    void scanFiltersByPrefix() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:a", new byte[]{1}, LONG_TTL);
        store.put("place:b", new byte[]{1}, LONG_TTL);
        store.put("trade:c", new byte[]{1}, LONG_TTL);

        Set<String> keys = adminOf(store).scan("place:", 10).stream()
                .map(JobsKVStoreAdmin.Entry::key)
                .collect(Collectors.toSet());
        assertEquals(Set.of("place:a", "place:b"), keys);
    }

    @Test
    void scanWithEmptyPrefixReturnsEverything() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:a", new byte[]{1}, LONG_TTL);
        store.put("trade:c", new byte[]{1}, LONG_TTL);
        assertEquals(2, adminOf(store).scan("", 10).size());
    }

    @Test
    void scanRespectsLimit() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        for (int i = 0; i < 10; i++) {
            store.put("place:" + i, new byte[]{1}, LONG_TTL);
        }
        assertEquals(3, adminOf(store).scan("place:", 3).size());
        assertTrue(adminOf(store).scan("place:", 0).isEmpty());
    }

    @Test
    void scanSkipsExpiredEntries() throws InterruptedException {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:live", new byte[]{1}, LONG_TTL);
        store.put("place:dead", new byte[]{1}, Duration.ofMillis(20));
        Thread.sleep(60);

        List<JobsKVStoreAdmin.Entry> entries = adminOf(store).scan("place:", 10);
        assertEquals(1, entries.size());
        assertEquals("place:live", entries.get(0).key());
    }

    @Test
    void scanReturnsIndependentValueCopies() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:a", new byte[]{1, 2}, LONG_TTL);
        adminOf(store).scan("place:", 10).get(0).value()[0] = 99;
        assertArrayEquals(new byte[]{1, 2}, store.get("place:a").orElseThrow());
    }

    @Test
    void countSkipsExpiredEntries() throws InterruptedException {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:live", new byte[]{1}, LONG_TTL);
        store.put("place:dead", new byte[]{1}, Duration.ofMillis(20));
        store.put("trade:x", new byte[]{1}, LONG_TTL);
        Thread.sleep(60);

        assertEquals(1, adminOf(store).count("place:"));
        assertEquals(2, adminOf(store).count(""));
    }

    @Test
    void inspectReportsRemainingTtl() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:a", new byte[]{7}, Duration.ofSeconds(30));

        JobsKVStoreAdmin.Entry entry = adminOf(store).inspect("place:a").orElseThrow();
        assertEquals("place:a", entry.key());
        assertArrayEquals(new byte[]{7}, entry.value());
        assertTrue(entry.remainingTtl().toMillis() > 0, "TTL must be positive");
        assertTrue(entry.remainingTtl().compareTo(Duration.ofSeconds(30)) <= 0,
                "TTL must not exceed the configured duration");
    }

    @Test
    void inspectReturnsEmptyForMissingOrExpired() throws InterruptedException {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:dead", new byte[]{1}, Duration.ofMillis(20));
        Thread.sleep(60);

        assertTrue(adminOf(store).inspect("place:nope").isEmpty());
        assertTrue(adminOf(store).inspect("place:dead").isEmpty());
    }

    @Test
    void removeByPrefixRemovesOnlyMatchingKeys() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:a", new byte[]{1}, LONG_TTL);
        store.put("place:b", new byte[]{1}, LONG_TTL);
        store.put("trade:c", new byte[]{1}, LONG_TTL);

        assertEquals(2, adminOf(store).removeByPrefix("place:"));
        assertFalse(store.get("place:a").isPresent());
        assertTrue(store.get("trade:c").isPresent());
    }

    @Test
    void removeByPrefixCountsExpiredLeftovers() throws InterruptedException {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:dead", new byte[]{1}, Duration.ofMillis(20));
        Thread.sleep(60);
        // get で回収されていない期限切れ entry も「掃除した件数」に含める。
        assertEquals(1, adminOf(store).removeByPrefix("place:"));
        assertEquals(0, adminOf(store).size());
    }

    @Test
    void removeByPrefixWithEmptyPrefixClearsEverything() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("place:a", new byte[]{1}, LONG_TTL);
        store.put("trade:c", new byte[]{1}, LONG_TTL);

        assertEquals(2, adminOf(store).removeByPrefix(""));
        assertEquals(0, adminOf(store).size());
    }
}
