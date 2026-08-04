package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.KvsKeys;
import org.bukkit.block.Block;

import java.time.Duration;

/**
 * BlockPlaceListener から呼ばれ、置かれた block の位置について KVS に
 * "置いた" マーカーを書く。TTL は AntiAutomationConfig.recentlyPlacedBreak.windowSec。
 *
 * spec/04-reward-pipeline.md 「自動化対策 - recently_placed_break」。
 *
 * <p>Ageable (作物) も含めて全ての block を記録する。作物を記録しないと
 * 「種を植える → 壊す → また植える」の再設置ループを検出できないため (ADR-0023)。
 * 破壊側で作物を対象外にする判断 (ADR-0016) は {@link RecentlyPlacedBreakCheck} が持つ。
 */
public final class PlacementRecorder {

    private static final byte[] MARKER = new byte[] {1};

    private final JobsKVStore kvStore;

    public PlacementRecorder(JobsKVStore kvStore) {
        this.kvStore = kvStore;
    }

    /**
     * @param windowSec 「置かれてから何秒以内の破壊・再設置を 0 にするか」
     */
    public void recordPlacement(Block block, int windowSec) {
        String key = KvsKeys.place(
                block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()
        );
        kvStore.put(key, MARKER, Duration.ofSeconds(windowSec));
    }
}
