package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.api.event.JobActionPaidEvent;
import me.f0reach.jobs.persistence.async.ActionLogWriteQueue;
import me.f0reach.jobs.persistence.async.BatchFlushWorker;
import me.f0reach.jobs.persistence.dto.ActionLogRow;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 段階 10。ActionLogWriteQueue に enqueue し、JobActionPaidEvent を async 発火する。
 *
 * <p>spec/04-reward-pipeline.md 「行動ログを書く」および spec/06-public-api.md を参照。
 * BatchFlushWorker#isBackpressure が true のときは enqueue を諦めて WARNING を残す (threading.md)。
 */
public final class ActionLogStage implements Stage {

    private final Plugin plugin;
    private final ActionLogWriteQueue queue;
    private final BatchFlushWorker worker;
    private final AsyncExecutor asyncExecutor;

    public ActionLogStage(
            Plugin plugin,
            ActionLogWriteQueue queue,
            BatchFlushWorker worker,
            AsyncExecutor asyncExecutor
    ) {
        this.plugin = plugin;
        this.queue = queue;
        this.worker = worker;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public Affinity affinity() { return Affinity.WORKER; }

    @Override
    public Result execute(PipelineContext ctx) {
        ActionLogRow row = new ActionLogRow(
                ctx.playerUuid(),
                ctx.jobId().value(),
                ctx.derivedKey().value(),
                ctx.baseReward(),
                ctx.finalReward(),
                ctx.rareHit(),
                ctx.amount(),
                ctx.occurredAt()
        );

        if (worker.isBackpressure()) {
            plugin.getLogger().warning(
                    "action_log backpressure: dropping row for " + ctx.playerName()
                            + " key=" + ctx.derivedKey().value()
            );
        } else if (!queue.offer(row)) {
            plugin.getLogger().warning(
                    "action_log queue full: dropping row for " + ctx.playerName()
                            + " key=" + ctx.derivedKey().value()
            );
        }

        // async event。net_paid が 0 でも購読者が居るので発火する（Quest 側の 0 円ログにも使う余地）。
        // この Stage 自体が main thread 外で走るため、AsyncExecutor へ投げ直さず直接発火する。
        JobActionPaidEvent event = new JobActionPaidEvent(
                ctx.player(),
                ctx.jobId().value(),
                ctx.derivedKey().value(),
                ctx.baseReward(),
                ctx.finalReward(),
                ctx.netPaid(),
                ctx.rareHit(),
                ctx.amount(),
                ctx.occurredAt()
        );
        fireAsync(event);

        return Result.CONTINUE;
    }

    /**
     * {@link JobActionPaidEvent} は async event なので main thread からは発火できない。
     * {@code reward.async.enabled: false} のときはこの Stage も main thread で走るため、
     * その場合だけ {@link AsyncExecutor} へ逃がす。
     */
    private void fireAsync(JobActionPaidEvent event) {
        if (Bukkit.isPrimaryThread()) {
            asyncExecutor.runAsync(() -> Bukkit.getPluginManager().callEvent(event));
            return;
        }
        Bukkit.getPluginManager().callEvent(event);
    }
}
