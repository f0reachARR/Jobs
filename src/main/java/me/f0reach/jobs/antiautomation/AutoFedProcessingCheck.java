package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.UUID;

/**
 * auto_fed_processing: op:&lt;kind&gt;:&lt;coords&gt; の operator が null または未登録なら 0。
 * spec/04-reward-pipeline.md 「自動化対策」4 番目 (ADR-0017)。
 *
 * <p>item_smelted / item_brewed の 2 種類に適用。
 * operator の書き込み・null 上書きは {@link OperatorTracker} が担当する。
 */
public final class AutoFedProcessingCheck implements AntiAutomationCheck {

    public static final String REASON = "auto_fed_processing";

    private final JobsKVStore kvStore;

    public AutoFedProcessingCheck(JobsKVStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public boolean appliesTo(PipelineContext ctx, ActionType actionType) {
        if (actionType != ActionType.ITEM_SMELTED && actionType != ActionType.ITEM_BREWED) return false;
        AntiAutomationConfig cfg = ctx.jobDefinition().antiAutomation();
        return cfg != null && cfg.autoFedProcessing() != null && cfg.autoFedProcessing().enabled();
    }

    @Override
    public String evaluate(PipelineContext ctx) {
        Block container = ctx.subject().containerBlock();
        ContainerKind kind = ctx.subject().containerKind();
        if (container == null || kind == null) return null;

        String key = KvsKeys.op(
                kind.tag(),
                container.getWorld().getUID(),
                container.getX(), container.getY(), container.getZ()
        );
        Optional<byte[]> raw = kvStore.get(key);
        if (raw.isEmpty()) return REASON;
        UUID operator = OperatorTracker.decodeOperator(raw.get());
        return operator == null ? REASON : null;
    }
}
