package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.KvsKeys;

import java.time.Duration;
import java.util.UUID;

/**
 * VillagerTradeListener の取引成立で呼ばれ、
 * (villager × recipe) の組み合わせに cooldown_sec の TTL でマーカーを書く。
 */
public final class TradeRecorder {

    private static final byte[] MARKER = new byte[] {1};

    private final JobsKVStore kvStore;

    public TradeRecorder(JobsKVStore kvStore) {
        this.kvStore = kvStore;
    }

    public void recordTrade(UUID villager, int recipeIndex, int cooldownSec) {
        kvStore.put(KvsKeys.trade(villager, recipeIndex), MARKER, Duration.ofSeconds(cooldownSec));
    }
}
