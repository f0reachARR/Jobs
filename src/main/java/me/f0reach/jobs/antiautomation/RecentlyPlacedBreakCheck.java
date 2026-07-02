package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;

/**
 * recently_placed_break: KVS に place:* が残っていれば 0。
 * spec/04-reward-pipeline.md 「自動化対策」3 番目 (ADR-0016)。
 *
 * <p>Ageable block は対象外 (unplanted_crop_harvest 側で扱う)。
 * TTL は BlockPlace 時点で置く (PlacementRecorder)。
 */
public final class RecentlyPlacedBreakCheck implements AntiAutomationCheck {

    public static final String REASON = "recently_placed_break";

    private final JobsKVStore kvStore;

    public RecentlyPlacedBreakCheck(JobsKVStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public boolean appliesTo(PipelineContext ctx, ActionType actionType) {
        if (actionType != ActionType.BLOCK_BROKEN) return false;
        AntiAutomationConfig cfg = ctx.jobDefinition().antiAutomation();
        return cfg != null && cfg.recentlyPlacedBreak() != null;
    }

    @Override
    public String evaluate(PipelineContext ctx) {
        Block block = ctx.subject().block();
        if (block == null) return null;
        if (block.getBlockData() instanceof Ageable) return null; // 作物は対象外

        String key = KvsKeys.place(
                block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()
        );
        return kvStore.get(key).isPresent() ? REASON : null;
    }
}
