package me.f0reach.jobs.modifier;

import me.f0reach.jobs.api.extension.JobRewardContext;
import me.f0reach.jobs.api.extension.JobRewardSplitContext;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.entity.Player;

/**
 * PipelineContext を api.extension の {@link JobRewardContext} / {@link JobRewardSplitContext}
 * にアダプトする内部型。
 *
 * <p>{@code getCurrentReward()} と {@code getRewardAfterModifiers()} は
 * chain 実行途中の値をコンストラクタで受け取る。
 */
public final class PipelineJobRewardContext implements JobRewardSplitContext {

    private final PipelineContext inner;
    private final double currentReward;
    private final Double rewardAfterModifiers;

    /** Modifier 用。 */
    public PipelineJobRewardContext(PipelineContext inner, double currentReward) {
        this(inner, currentReward, null);
    }

    /** Splitter 用。rewardAfterModifiers に Modifier 適用後の値を渡す。 */
    public PipelineJobRewardContext(PipelineContext inner, double currentReward, Double rewardAfterModifiers) {
        this.inner = inner;
        this.currentReward = currentReward;
        this.rewardAfterModifiers = rewardAfterModifiers;
    }

    @Override
    public Player getPlayer() { return inner.player(); }

    @Override
    public String getJobId() { return inner.jobId().value(); }

    @Override
    public String getActionKey() { return inner.derivedKey().value(); }

    @Override
    public double getCurrentReward() { return currentReward; }

    @Override
    public boolean isRareHit() { return inner.rareHit(); }

    @Override
    public double getRewardAfterModifiers() {
        return rewardAfterModifiers == null ? currentReward : rewardAfterModifiers;
    }
}
