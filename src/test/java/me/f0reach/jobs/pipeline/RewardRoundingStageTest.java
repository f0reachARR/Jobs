package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.detection.DetectedAction;
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
import me.f0reach.jobs.pipeline.stage.RewardRoundingStage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardRoundingStageTest {

    private ServerMock server;

    @BeforeEach
    void setUp() { server = MockBukkit.mock(); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private PipelineContext ctxWith(double base, double finalR, double netPaid) {
        Player player = server.addPlayer();
        MatchCriteria criteria = new MatchCriteria.EntityKilled(
                new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(
                ActionType.ENTITY_KILLED, criteria,
                new RewardAmount.Fixed(0.0), null,
                new ActionKey("kill:minecraft:zombie"));
        JobDefinition job = new JobDefinition(
                new JobId("combat"), "Combat", NamespacedKey.minecraft("iron_sword"),
                List.of(entry), VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());
        DetectedAction action = new DetectedAction(
                player, new JobId("combat"), entry, entry.derivedKey(), 1, SourceFlags.none());
        PipelineContext ctx = new PipelineContext(action, job, Instant.now());
        ctx.setBaseReward(base);
        ctx.setFinalReward(finalR);
        ctx.setNetPaid(netPaid);
        return ctx;
    }

    @Test
    void decimalsZeroRoundsToInteger() {
        RewardRoundingStage s = new RewardRoundingStage(
                MockBukkit.createMockPlugin(),
                new PluginConfig.RewardConfig(0, RoundingMode.HALF_UP));
        PipelineContext c = ctxWith(1.5, 2.4, 3.6);
        s.execute(c);
        assertEquals(2.0, c.baseReward());
        assertEquals(2.0, c.finalReward());
        assertEquals(4.0, c.netPaid());
    }

    @Test
    void decimalsTwoKeepsCentPrecision() {
        RewardRoundingStage s = new RewardRoundingStage(
                MockBukkit.createMockPlugin(),
                new PluginConfig.RewardConfig(2, RoundingMode.HALF_UP));
        PipelineContext c = ctxWith(1.235, 2.005, 3.499);
        s.execute(c);
        assertEquals(1.24, c.baseReward());
        assertEquals(2.01, c.finalReward());
        assertEquals(3.50, c.netPaid());
    }

    @Test
    void floorTruncatesTowardZero() {
        RewardRoundingStage s = new RewardRoundingStage(
                MockBukkit.createMockPlugin(),
                new PluginConfig.RewardConfig(0, RoundingMode.FLOOR));
        PipelineContext c = ctxWith(1.9, 2.5, 0.1);
        s.execute(c);
        assertEquals(1.0, c.baseReward());
        assertEquals(2.0, c.finalReward());
        assertEquals(0.0, c.netPaid());
    }

    @Test
    void unnecessaryWithFractionZeroesRewardAndContinues() {
        RewardRoundingStage s = new RewardRoundingStage(
                MockBukkit.createMockPlugin(),
                new PluginConfig.RewardConfig(0, RoundingMode.UNNECESSARY));
        PipelineContext c = ctxWith(1.5, 2.5, 3.5);
        s.execute(c);
        assertEquals(0.0, c.baseReward());
        assertEquals(0.0, c.finalReward());
        assertEquals(0.0, c.netPaid());
    }
}
