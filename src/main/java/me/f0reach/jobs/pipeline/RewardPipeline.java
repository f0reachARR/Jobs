package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.detection.DetectedAction;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.registry.JobRegistry;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

/**
 * 各 Stage を順に呼ぶ実行器。spec/04-reward-pipeline.md を参照。
 *
 * <p>Stage の例外は catch し、その Stage を skip して次に進む（[04-reward-pipeline.md]
 * 「エラーハンドリング」節）。
 */
public final class RewardPipeline {

    private final Plugin plugin;
    private final JobRegistry jobRegistry;
    private final List<Stage> stages;
    private final Clock clock;

    public RewardPipeline(Plugin plugin, JobRegistry jobRegistry, List<Stage> stages) {
        this(plugin, jobRegistry, stages, Clock.systemUTC());
    }

    public RewardPipeline(Plugin plugin, JobRegistry jobRegistry, List<Stage> stages, Clock clock) {
        this.plugin = plugin;
        this.jobRegistry = jobRegistry;
        this.stages = List.copyOf(stages);
        this.clock = clock;
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

        for (Stage stage : stages) {
            try {
                Stage.Result result = stage.execute(ctx);
                if (result == Stage.Result.HALT) return;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Stage " + stage.getClass().getSimpleName() + " threw", e);
            }
        }
    }
}
