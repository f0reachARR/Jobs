package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.detection.DetectedAction;
import me.f0reach.jobs.detection.DetectionSubject;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionKey;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RewardAmount;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import me.f0reach.jobs.pipeline.async.RewardDispatcher;
import me.f0reach.jobs.registry.JobRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RewardPipeline} が affinity で prologue と worker の 2 ブロックに分かれることを検証する。
 * docs/plan/async-reward-pipeline.md 「Stage の thread affinity」を参照。
 */
class RewardPipelineSplitTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    /** 実行順を記録するだけの Stage。 */
    private static final class Recorder implements Stage {
        private final String name;
        private final Affinity affinity;
        private final Result result;
        private final List<String> log;

        Recorder(String name, Affinity affinity, Result result, List<String> log) {
            this.name = name;
            this.affinity = affinity;
            this.result = result;
            this.log = log;
        }

        @Override public Affinity affinity() { return affinity; }

        @Override
        public Result execute(PipelineContext ctx) {
            log.add(name);
            return result;
        }
    }

    /** 投入されたタスクを保持して、実行タイミングをテストが決められる dispatcher。 */
    private static final class DeferredDispatcher implements RewardDispatcher {
        final List<Runnable> rewards = new ArrayList<>();
        boolean accept = true;

        @Override
        public boolean dispatchReward(Runnable task) {
            if (!accept) return false;
            rewards.add(task);
            return true;
        }

        @Override
        public void dispatchControl(Runnable task) { task.run(); }
    }

    private JobDefinition job() {
        MatchCriteria c = new MatchCriteria.EntityKilled(
                new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(
                ActionType.ENTITY_KILLED, c, new RewardAmount.Fixed(10.0), null,
                new ActionKey("kill:minecraft:zombie"));
        return new JobDefinition(
                new JobId("combat"), "Combat", null, NamespacedKey.minecraft("iron_sword"),
                List.of(entry), VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty()
        );
    }

    private DetectedAction action(Player player, JobDefinition job, DetectionSubject subject) {
        return new DetectedAction(
                player, job.id(), job.rewards().get(0),
                job.rewards().get(0).derivedKey(), 1, SourceFlags.none(), subject
        );
    }

    private JobRegistry registryWith(JobDefinition job) {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job));
        return registry;
    }

    @Test
    void prologueRunsInlineAndWorkerStagesAreDeferred() {
        JobDefinition job = job();
        Player player = server.addPlayer();
        List<String> log = new ArrayList<>();
        DeferredDispatcher dispatcher = new DeferredDispatcher();

        RewardPipeline pipeline = new RewardPipeline(plugin, registryWith(job), dispatcher, List.of(
                new Recorder("main1", Stage.Affinity.MAIN, Stage.Result.CONTINUE, log),
                new Recorder("main2", Stage.Affinity.MAIN, Stage.Result.CONTINUE, log),
                new Recorder("worker1", Stage.Affinity.WORKER, Stage.Result.CONTINUE, log),
                new Recorder("worker2", Stage.Affinity.WORKER, Stage.Result.CONTINUE, log)
        ));
        assertEquals(2, pipeline.prologueSize());
        assertEquals(2, pipeline.workerStageSize());

        pipeline.run(action(player, job, DetectionSubject.empty()));

        // prologue だけが同期実行され、worker ブロックは dispatcher に預けられている。
        assertEquals(List.of("main1", "main2"), log);
        assertEquals(1, dispatcher.rewards.size());

        dispatcher.rewards.get(0).run();
        assertEquals(List.of("main1", "main2", "worker1", "worker2"), log);
    }

    @Test
    void haltInPrologueSkipsTheWorkerBlockEntirely() {
        JobDefinition job = job();
        Player player = server.addPlayer();
        List<String> log = new ArrayList<>();
        DeferredDispatcher dispatcher = new DeferredDispatcher();

        RewardPipeline pipeline = new RewardPipeline(plugin, registryWith(job), dispatcher, List.of(
                new Recorder("main1", Stage.Affinity.MAIN, Stage.Result.HALT, log),
                new Recorder("main2", Stage.Affinity.MAIN, Stage.Result.CONTINUE, log),
                new Recorder("worker1", Stage.Affinity.WORKER, Stage.Result.CONTINUE, log)
        ));
        pipeline.run(action(player, job, DetectionSubject.empty()));

        assertEquals(List.of("main1"), log);
        assertTrue(dispatcher.rewards.isEmpty());
    }

    @Test
    void haltInWorkerBlockStopsTheRemainingWorkerStages() {
        JobDefinition job = job();
        Player player = server.addPlayer();
        List<String> log = new ArrayList<>();
        DeferredDispatcher dispatcher = new DeferredDispatcher();

        RewardPipeline pipeline = new RewardPipeline(plugin, registryWith(job), dispatcher, List.of(
                new Recorder("worker1", Stage.Affinity.WORKER, Stage.Result.HALT, log),
                new Recorder("worker2", Stage.Affinity.WORKER, Stage.Result.CONTINUE, log)
        ));
        pipeline.run(action(player, job, DetectionSubject.empty()));
        dispatcher.rewards.get(0).run();

        assertEquals(List.of("worker1"), log);
    }

    @Test
    void bukkitRefsAreDetachedBeforeTheWorkerBlockRuns() {
        JobDefinition job = job();
        Player player = server.addPlayer();
        DeferredDispatcher dispatcher = new DeferredDispatcher();
        List<DetectionSubject> observed = new ArrayList<>();

        RewardPipeline pipeline = new RewardPipeline(plugin, registryWith(job), dispatcher, List.of(
                new Stage() {
                    @Override
                    public Result execute(PipelineContext ctx) {
                        // prologue では Block 参照がまだ見える。
                        observed.add(ctx.subject());
                        return Result.CONTINUE;
                    }
                },
                new Stage() {
                    @Override public Affinity affinity() { return Affinity.WORKER; }

                    @Override
                    public Result execute(PipelineContext ctx) {
                        observed.add(ctx.subject());
                        return Result.CONTINUE;
                    }
                }
        ));

        DetectionSubject subject = DetectionSubject.builder()
                .villagerUuid(java.util.UUID.randomUUID())
                .recipeIndex(3)
                .build();
        pipeline.run(action(player, job, subject));
        dispatcher.rewards.get(0).run();

        assertEquals(2, observed.size());
        assertNotNull(observed.get(0).villagerUuid());
        // detach 後は empty に差し替わっている。
        assertEquals(DetectionSubject.empty(), observed.get(1));
    }

    @Test
    void droppedRewardDoesNotThrow() {
        JobDefinition job = job();
        Player player = server.addPlayer();
        List<String> log = new ArrayList<>();
        DeferredDispatcher dispatcher = new DeferredDispatcher();
        dispatcher.accept = false;

        RewardPipeline pipeline = new RewardPipeline(plugin, registryWith(job), dispatcher, List.of(
                new Recorder("worker1", Stage.Affinity.WORKER, Stage.Result.CONTINUE, log)
        ));
        pipeline.run(action(player, job, DetectionSubject.empty()));

        assertTrue(log.isEmpty());
        assertTrue(dispatcher.rewards.isEmpty());
    }

    @Test
    void mainStageAfterWorkerStageIsRejectedAtWiringTime() {
        List<String> log = new ArrayList<>();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                new RewardPipeline(plugin, new JobRegistry(), new DeferredDispatcher(), List.of(
                        new Recorder("worker1", Stage.Affinity.WORKER, Stage.Result.CONTINUE, log),
                        new Recorder("main1", Stage.Affinity.MAIN, Stage.Result.CONTINUE, log)
                )));
        assertTrue(e.getMessage().contains("declared after a WORKER stage"));
    }

    @Test
    void stageExceptionIsSwallowedAndTheBlockContinues() {
        JobDefinition job = job();
        Player player = server.addPlayer();
        List<String> log = new ArrayList<>();
        DeferredDispatcher dispatcher = new DeferredDispatcher();

        RewardPipeline pipeline = new RewardPipeline(plugin, registryWith(job), dispatcher, List.of(
                new Stage() {
                    @Override
                    public Result execute(PipelineContext ctx) {
                        throw new IllegalStateException("boom");
                    }
                },
                new Recorder("main2", Stage.Affinity.MAIN, Stage.Result.CONTINUE, log)
        ));
        pipeline.run(action(player, job, DetectionSubject.empty()));

        assertEquals(List.of("main2"), log);
        assertFalse(dispatcher.rewards.size() > 0);
    }
}
