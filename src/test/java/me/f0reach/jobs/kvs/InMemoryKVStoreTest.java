package me.f0reach.jobs.kvs;

import me.f0reach.jobs.kvs.memory.InMemoryKVStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryKVStoreTest {

    @Test
    void putThenGetReturnsSameBytes() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        byte[] value = {1, 2, 3};
        store.put("k", value, Duration.ofMinutes(1));
        Optional<byte[]> got = store.get("k");
        assertTrue(got.isPresent());
        assertArrayEquals(value, got.get());
    }

    @Test
    void putStoresIndependentCopy() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        byte[] value = {1, 2, 3};
        store.put("k", value, Duration.ofMinutes(1));
        value[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, store.get("k").get());
    }

    @Test
    void getReturnsIndependentCopy() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("k", new byte[]{1, 2, 3}, Duration.ofMinutes(1));
        byte[] a = store.get("k").get();
        a[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, store.get("k").get());
    }

    @Test
    void expiredEntryIsInvisible() throws InterruptedException {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("k", new byte[]{1}, Duration.ofMillis(20));
        Thread.sleep(60);
        assertFalse(store.get("k").isPresent());
    }

    @Test
    void removeMakesEntryInvisible() {
        InMemoryKVStore store = new InMemoryKVStore(100);
        store.put("k", new byte[]{1}, Duration.ofMinutes(1));
        store.remove("k");
        assertFalse(store.get("k").isPresent());
    }

    @Test
    void evictsWhenOverCapacityExpired() throws InterruptedException {
        InMemoryKVStore store = new InMemoryKVStore(2);
        store.put("a", new byte[]{1}, Duration.ofMillis(10));
        Thread.sleep(30);
        store.put("b", new byte[]{2}, Duration.ofMinutes(1));
        store.put("c", new byte[]{3}, Duration.ofMinutes(1));
        // 'a' は expire しており、b/c を書き込む際の evict で消える。
        assertEquals(2, store.size());
        assertFalse(store.get("a").isPresent());
        assertTrue(store.get("b").isPresent());
        assertTrue(store.get("c").isPresent());
    }
}
