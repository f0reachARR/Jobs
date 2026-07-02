package me.f0reach.jobs.antiautomation;

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
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AntiAutomationCoordinatorTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private PipelineContext ctx(AntiAutomationConfig cfg, ActionType actionType) {
        Player player = server.addPlayer();
        MatchCriteria criteria = new MatchCriteria.EntityKilled(
                new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(
                actionType, criteria, new RewardAmount.Fixed(10.0), null,
                new ActionKey("kill:minecraft:zombie"));
        JobDefinition job = new JobDefinition(
                new JobId("combat"), "Combat", NamespacedKey.minecraft("iron_sword"),
                List.of(entry), VarietyPenaltyConfig.disabled(), cfg
        );
        DetectedAction action = new DetectedAction(
                player, job.id(), entry, entry.derivedKey(), 1, SourceFlags.none(),
                DetectionSubject.empty()
        );
        return new PipelineContext(action, job, Instant.now());
    }

    @Test
    void firstZeroReasonWinsAndLaterChecksAreShortCircuited() {
        AntiAutomationCheck alwaysZero = new AntiAutomationCheck() {
            @Override public boolean appliesTo(PipelineContext ctx, ActionType t) { return true; }
            @Override public String evaluate(PipelineContext ctx) { return "reason_a"; }
        };
        AntiAutomationCheck neverCalled = new AntiAutomationCheck() {
            @Override public boolean appliesTo(PipelineContext ctx, ActionType t) {
                throw new AssertionError("must not be called");
            }
            @Override public String evaluate(PipelineContext ctx) { return "reason_b"; }
        };
        AntiAutomationCoordinator coord = new AntiAutomationCoordinator(plugin, List.of(alwaysZero, neverCalled));
        String r = coord.firstZero(ctx(AntiAutomationConfig.empty(), ActionType.ENTITY_KILLED),
                ActionType.ENTITY_KILLED);
        assertEquals("reason_a", r);
    }

    @Test
    void allNullChecksReturnNull() {
        AntiAutomationCheck noop = new AntiAutomationCheck() {
            @Override public boolean appliesTo(PipelineContext ctx, ActionType t) { return true; }
            @Override public String evaluate(PipelineContext ctx) { return null; }
        };
        AntiAutomationCoordinator coord = new AntiAutomationCoordinator(plugin, List.of(noop));
        assertNull(coord.firstZero(ctx(AntiAutomationConfig.empty(), ActionType.ENTITY_KILLED),
                ActionType.ENTITY_KILLED));
    }

    @Test
    void checkExceptionIsSwallowedAndTreatedAsPass() {
        AntiAutomationCheck throwing = new AntiAutomationCheck() {
            @Override public boolean appliesTo(PipelineContext ctx, ActionType t) { return true; }
            @Override public String evaluate(PipelineContext ctx) { throw new RuntimeException("boom"); }
        };
        AntiAutomationCheck confirms = new AntiAutomationCheck() {
            @Override public boolean appliesTo(PipelineContext ctx, ActionType t) { return true; }
            @Override public String evaluate(PipelineContext ctx) { return "later_reason"; }
        };
        AntiAutomationCoordinator coord = new AntiAutomationCoordinator(plugin, List.of(throwing, confirms));
        assertEquals("later_reason",
                coord.firstZero(ctx(AntiAutomationConfig.empty(), ActionType.ENTITY_KILLED),
                        ActionType.ENTITY_KILLED));
    }

    @Test
    void appliesToFalseSkipsEvaluate() {
        AntiAutomationCheck neverEvaluated = new AntiAutomationCheck() {
            @Override public boolean appliesTo(PipelineContext ctx, ActionType t) { return false; }
            @Override public String evaluate(PipelineContext ctx) {
                throw new AssertionError("evaluate must not be called");
            }
        };
        AntiAutomationCoordinator coord = new AntiAutomationCoordinator(plugin, List.of(neverEvaluated));
        assertNull(coord.firstZero(ctx(AntiAutomationConfig.empty(), ActionType.ENTITY_KILLED),
                ActionType.ENTITY_KILLED));
    }
}
