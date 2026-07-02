package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.splitter.SplitterChain;

/**
 * 段階 8。Splitter chain を宣言順に適用し、netPaid を確定させる。
 * spec/04-reward-pipeline.md 「Splitter chain」を参照。
 *
 * <p>finalReward は変更しない。Splitter が削った差分は netPaid にのみ反映する。
 * zeroLocked のときは netPaid=0 のまま何もしない (PipelineContext のデフォルト)。
 */
public final class SplitterStage implements Stage {

    private final SplitterChain chain;

    public SplitterStage(SplitterChain chain) {
        this.chain = chain;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.zeroLocked()) {
            ctx.setNetPaid(0.0);
            return Result.CONTINUE;
        }
        // Splitter が register されていない環境でも、netPaid = finalReward を確定する。
        double net = chain.applyAndComputeNet(ctx);
        ctx.setNetPaid(net);
        return Result.CONTINUE;
    }
}
