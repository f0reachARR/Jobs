package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.domain.job.RewardAmount;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;

import java.util.random.RandomGenerator;

/**
 * 段階 4。RewardAmount を解決して amount 倍する。
 *
 * spec/04-reward-pipeline.md 「基礎報酬」を参照。
 * item_smelted / item_crafted / villager_traded / item_brewed のみ amount > 1 になる。
 */
public final class BaseRewardStage implements Stage {

    private final RandomGenerator random;

    public BaseRewardStage(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.zeroLocked()) return Result.CONTINUE;
        RewardAmount amount = ctx.matchedEntry().rewardAmount();
        double base = amount.roll(random) * Math.max(1, ctx.amount());
        ctx.setBaseReward(base);
        ctx.setFinalReward(base);
        return Result.CONTINUE;
    }
}
