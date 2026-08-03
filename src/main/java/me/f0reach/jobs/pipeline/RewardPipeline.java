package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.detection.DetectedAction;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.pipeline.async.RewardDispatcher;
import me.f0reach.jobs.registry.JobRegistry;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 各 Stage を順に呼ぶ実行器。spec/04-reward-pipeline.md を参照。
 *
 * <p>{@link Stage#affinity()} で prologue（main thread）と worker の 2 ブロックに分ける
 * （docs/plan/async-reward-pipeline.md）。prologue は listener の呼び出しスレッドで
 * そのまま実行し、HALT が返らなければ残りを {@link RewardDispatcher} へ投入する。
 * prologue をスケジューラ越しにしないのは、{@code Block} と {@code Entity} 参照を
 * 同 tick 内で使い切る必要があるためである。
 *
 * <p>Stage の例外は catch し、その Stage を skip して次に進む（[04-reward-pipeline.md]
 * 「エラーハンドリング」節）。
 */
public final class RewardPipeline {

    private final Plugin plugin;
    private final JobRegistry jobRegistry;
    private final RewardDispatcher dispatcher;
    private final List<Stage> prologue;
    private final List<Stage> workerStages;
    private final Clock clock;

    public RewardPipeline(
            Plugin plugin,
            JobRegistry jobRegistry,
            RewardDispatcher dispatcher,
            List<Stage> stages
    ) {
        this(plugin, jobRegistry, dispatcher, stages, Clock.systemUTC());
    }

    public RewardPipeline(
            Plugin plugin,
            JobRegistry jobRegistry,
            RewardDispatcher dispatcher,
            List<Stage> stages,
            Clock clock
    ) {
        this.plugin = plugin;
        this.jobRegistry = jobRegistry;
        this.dispatcher = dispatcher;
        this.clock = clock;

        // 宣言順のまま affinity で 2 分割する。MAIN が WORKER の後ろに来る並びは
        // hop が増えるだけで得が無いので、wiring 側の誤りとして弾く。
        List<Stage> main = new ArrayList<>();
        List<Stage> worker = new ArrayList<>();
        for (Stage stage : stages) {
            if (stage.affinity() == Stage.Affinity.WORKER) {
                worker.add(stage);
            } else if (worker.isEmpty()) {
                main.add(stage);
            } else {
                throw new IllegalArgumentException(
                        "MAIN stage " + stage.getClass().getSimpleName()
                                + " declared after a WORKER stage; reorder the pipeline");
            }
        }
        this.prologue = List.copyOf(main);
        this.workerStages = List.copyOf(worker);
    }

    public void run(DetectedAction action) {
        JobDefinition job = jobRegistry.get(action.matchedJobId()).orElse(null);
        if (job == null) {
            plugin.getLogger().warning(
                    "Job '" + action.matchedJobId() + "' vanished from registry during pipeline"
            );
            return;
        }
        PipelineContext ctx = new PipelineContext(action, job, Instant.now(clock));

        if (!execute(prologue, ctx)) return;
        if (workerStages.isEmpty()) return;

        // 同 tick 内でしか有効でない Block / Entity 参照をここで捨てる。
        ctx.detachBukkitRefs();

        if (!dispatcher.dispatchReward(() -> execute(workerStages, ctx))) {
            // キュー溢れ。RewardWorkQueue 側が件数をまとめて WARNING に出すので、
            // ここでは 1 件ごとのログを足さない。
            plugin.getLogger().fine(
                    () -> "reward dropped for " + ctx.playerName() + " key=" + ctx.derivedKey().value());
        }
    }

    /** ブロックを頭から実行する。HALT が返ったら false。 */
    private boolean execute(List<Stage> stages, PipelineContext ctx) {
        for (Stage stage : stages) {
            try {
                if (stage.execute(ctx) == Stage.Result.HALT) return false;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Stage " + stage.getClass().getSimpleName() + " threw", e);
            }
        }
        return true;
    }

    /** 起動時の検査とテスト向け。prologue に入った Stage 数。 */
    public int prologueSize() { return prologue.size(); }

    /** 起動時の検査とテスト向け。worker ブロックに入った Stage 数。 */
    public int workerStageSize() { return workerStages.size(); }
}
