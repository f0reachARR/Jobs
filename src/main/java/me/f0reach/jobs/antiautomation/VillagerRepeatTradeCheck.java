package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.pipeline.PipelineContext;

import java.util.UUID;

/**
 * villager_repeat_trade: KVS の trade:&lt;villager&gt;:&lt;recipe&gt; が残っていれば 0。
 * spec/04-reward-pipeline.md 「自動化対策」5 番目。
 */
public final class VillagerRepeatTradeCheck implements AntiAutomationCheck {

    public static final String REASON = "villager_repeat_trade";

    private final JobsKVStore kvStore;

    public VillagerRepeatTradeCheck(JobsKVStore kvStore) {
        this.kvStore = kvStore;
    }

    @Override
    public boolean appliesTo(PipelineContext ctx, ActionType actionType) {
        if (actionType != ActionType.VILLAGER_TRADED) return false;
        AntiAutomationConfig cfg = ctx.jobDefinition().antiAutomation();
        return cfg != null && cfg.villagerRepeatTrade() != null;
    }

    @Override
    public String evaluate(PipelineContext ctx) {
        UUID villager = ctx.subject().villagerUuid();
        Integer recipeIdx = ctx.subject().recipeIndex();
        if (villager == null || recipeIdx == null) return null;
        return kvStore.get(KvsKeys.trade(villager, recipeIdx)).isPresent() ? REASON : null;
    }
}
