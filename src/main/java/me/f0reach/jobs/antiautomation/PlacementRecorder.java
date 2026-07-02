package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.KvsKeys;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;

import java.time.Duration;

/**
 * BlockPlaceListener から呼ばれ、Ageable 以外の block について KVS に
 * "置いた" マーカーを書く。TTL は AntiAutomationConfig.recentlyPlacedBreak.windowSec。
 *
 * spec/04-reward-pipeline.md 「自動化対策 - recently_placed_break」。
 */
public final class PlacementRecorder {

    private static final byte[] MARKER = new byte[] {1};

    private final JobsKVStore kvStore;

    public PlacementRecorder(JobsKVStore kvStore) {
        this.kvStore = kvStore;
    }

    /**
     * @param windowSec 「置かれてから何秒以内の破壊を 0 にするか」
     */
    public void recordPlacement(Block block, int windowSec) {
        if (block.getBlockData() instanceof Ageable) return;
        String key = KvsKeys.place(
                block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()
        );
        kvStore.put(key, MARKER, Duration.ofSeconds(windowSec));
    }
}
