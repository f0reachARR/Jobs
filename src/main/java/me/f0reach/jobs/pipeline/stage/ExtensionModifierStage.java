package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.modifier.ExtensionModifierChain;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;

/**
 * 段階 7。外部拡張 Modifier chain を優先度順に適用する。
 * spec/04-reward-pipeline.md 「拡張 Modifier chain」を参照。
 */
public final class ExtensionModifierStage implements Stage {

    private final ExtensionModifierChain chain;

    public ExtensionModifierStage(ExtensionModifierChain chain) {
        this.chain = chain;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.zeroLocked()) return Result.CONTINUE;
        double reward = chain.apply(ctx);
        ctx.setFinalReward(reward);
        return Result.CONTINUE;
    }
}
