package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.antiautomation.AntiAutomationCheck;
import me.f0reach.jobs.antiautomation.AntiAutomationCoordinator;
import me.f0reach.jobs.antiautomation.AntiAutomationNotifier;
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
import me.f0reach.jobs.pipeline.stage.AntiAutomationStage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiAutomationStageTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private PipelineContext makeContext(Player player) {
        MatchCriteria criteria = new MatchCriteria.EntityKilled(
                new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(
                ActionType.ENTITY_KILLED, criteria, new RewardAmount.Fixed(10.0), null,
                new ActionKey("kill:minecraft:zombie"));
        JobDefinition job = new JobDefinition(
                new JobId("combat"), "Combat", null, NamespacedKey.minecraft("iron_sword"),
                List.of(entry), VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty()
        );
        DetectedAction action = new DetectedAction(
                player, job.id(), entry, entry.derivedKey(), 1, SourceFlags.none(),
                DetectionSubject.empty());
        return new PipelineContext(action, job, Instant.now());
    }

    private AntiAutomationCoordinator coordWithReason(String reason) {
        AntiAutomationCheck check = new AntiAutomationCheck() {
            @Override public boolean appliesTo(PipelineContext ctx, ActionType t) { return true; }
            @Override public String evaluate(PipelineContext ctx) { return reason; }
        };
        return new AntiAutomationCoordinator(plugin, List.of(check));
    }

    /** テスト用: notify() 呼び出しを記録するだけの stub。lang は引かない (I18n 非依存)。 */
    private static final class RecordingNotifier extends AntiAutomationNotifier {
        final List<String> calls = new ArrayList<>();
        RecordingNotifier() { super(null, java.util.Map.of()); }
        @Override public void notify(Player player, String reason) {
            calls.add(reason);
        }
    }

    @Test
    void zeroLockAndNotifierCalledOnReason() {
        Player player = server.addPlayer();
        PipelineContext ctx = makeContext(player);
        RecordingNotifier notifier = new RecordingNotifier();
        AntiAutomationStage stage = new AntiAutomationStage(
                coordWithReason("spawner_origin_kill"), notifier);

        Stage.Result r = stage.execute(ctx);

        assertEquals(Stage.Result.CONTINUE, r);
        assertTrue(ctx.zeroLocked());
        assertEquals(List.of("spawner_origin_kill"), notifier.calls);
    }

    @Test
    void noReasonMeansNoLockAndNoNotify() {
        Player player = server.addPlayer();
        PipelineContext ctx = makeContext(player);
        RecordingNotifier notifier = new RecordingNotifier();
        AntiAutomationStage stage = new AntiAutomationStage(
                coordWithReason(null), notifier);

        stage.execute(ctx);

        assertTrue(!ctx.zeroLocked());
        assertTrue(notifier.calls.isEmpty());
    }

    @Test
    void alreadyLockedSkipsCoordinatorAndNotifier() {
        Player player = server.addPlayer();
        PipelineContext ctx = makeContext(player);
        ctx.lockZero("previous_reason");
        AntiAutomationCheck exploding = new AntiAutomationCheck() {
            @Override public boolean appliesTo(PipelineContext ctx2, ActionType t) {
                throw new AssertionError("coordinator must not be called");
            }
            @Override public String evaluate(PipelineContext ctx2) { return "boom"; }
        };
        RecordingNotifier notifier = new RecordingNotifier();
        AntiAutomationStage stage = new AntiAutomationStage(
                new AntiAutomationCoordinator(plugin, List.of(exploding)), notifier);

        stage.execute(ctx);

        assertTrue(ctx.zeroLocked());
        assertTrue(notifier.calls.isEmpty());
    }
}
