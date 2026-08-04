package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.block.Block;

/**
 * recently_placed_replace: 直近に置かれた位置への「再設置」を 0 にする。
 * spec/04-reward-pipeline.md 「自動化対策」3 番目 (ADR-0016 / ADR-0023)。
 *
 * <p>{@link RecentlyPlacedBreakCheck} と ON/OFF・window を共有し
 * ({@code anti_automation.recently_placed_break})、KVS の {@code place:*} も共用する。
 * 置く → 壊す → また置く のループで {@code block_placed} 報酬を稼ぐ抜け道を塞ぐ。
 *
 * <p>破壊側と違い Ageable も対象にする。作物を除外すると
 * 「種を植える → 壊す → また植える」がそのまま抜け道として残るため (ADR-0023)。
 *
 * <p>この check は「今回の設置より前に置かれた記録」を読む必要がある。
 * {@code BlockPlaceListener} が dispatch を済ませてから
 * {@link PlacementRecorder} を呼ぶ順序に依存する。
 */
public final class RecentlyPlacedReplaceCheck implements AntiAutomationCheck {

    public static final String REASON = "recently_placed_replace";

    private final JobsKVStore kvStore;

    public RecentlyPlacedReplaceCheck(JobsKVStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public boolean appliesTo(PipelineContext ctx, ActionType actionType) {
        if (actionType != ActionType.BLOCK_PLACED) return false;
        AntiAutomationConfig cfg = ctx.jobDefinition().antiAutomation();
        return cfg != null && cfg.recentlyPlacedBreak() != null && cfg.recentlyPlacedBreak().enabled();
    }

    @Override
    public String evaluate(PipelineContext ctx) {
        Block block = ctx.subject().block();
        if (block == null) return null;

        String key = KvsKeys.place(
                block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()
        );
        return kvStore.get(key).isPresent() ? REASON : null;
    }
}
