package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.antiautomation.AntiAutomationCoordinator;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;

/**
 * 段階 3。AntiAutomationCoordinator を呼び、0 lock 判定を行う。
 * spec/04-reward-pipeline.md 「自動化対策」を参照。
 *
 * <p>0 lock されても以降の Stage は「0 のまま」進行する (ActionLog に 0 で残す)。
 * spec/04 「自動化対策：0 確定でもログには書く」の要件。
 */
public final class AntiAutomationStage implements Stage {

    private final AntiAutomationCoordinator coordinator;

    public AntiAutomationStage(AntiAutomationCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.zeroLocked()) return Result.CONTINUE;
        String reason = coordinator.firstZero(ctx, ctx.matchedEntry().actionType());
        if (reason != null) {
            ctx.lockZero(reason);
        }
        return Result.CONTINUE;
    }
}
