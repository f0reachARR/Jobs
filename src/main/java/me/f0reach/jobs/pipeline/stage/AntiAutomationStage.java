package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.antiautomation.AntiAutomationCoordinator;
import me.f0reach.jobs.antiautomation.AntiAutomationNotifier;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;

/**
 * 段階 3。AntiAutomationCoordinator を呼び、0 lock 判定を行う。
 * spec/04-reward-pipeline.md 「自動化対策」を参照。
 *
 * <p>0 lock されても以降の Stage は「0 のまま」進行する (ActionLog に 0 で残す)。
 * spec/04 「自動化対策：0 確定でもログには書く」の要件。
 *
 * <p>0 判定を発火したときに ActionBar でプレイヤーへ通知する (notifier)。
 * pipeline は main thread 前提なので {@link org.bukkit.entity.Player#sendActionBar} を直接呼ぶ。
 */
public final class AntiAutomationStage implements Stage {

    static final String BYPASS_PERMISSION = "jobs.bypass.anti-automation";

    private final AntiAutomationCoordinator coordinator;
    private final AntiAutomationNotifier notifier;

    public AntiAutomationStage(AntiAutomationCoordinator coordinator, AntiAutomationNotifier notifier) {
        this.coordinator = coordinator;
        this.notifier = notifier;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.zeroLocked()) return Result.CONTINUE;
        // jobs.bypass.anti-automation を持つプレイヤーは 6 種いずれの判定も走らせない。
        if (ctx.player().hasPermission(BYPASS_PERMISSION)) return Result.CONTINUE;
        String reason = coordinator.firstZero(ctx, ctx.matchedEntry().actionType());
        if (reason != null) {
            ctx.lockZero(reason);
            notifier.notify(ctx.player(), reason);
        }
        return Result.CONTINUE;
    }
}
