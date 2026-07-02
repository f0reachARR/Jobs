package me.f0reach.jobs.splitter;

import me.f0reach.jobs.api.extension.JobRewardSplitContext;
import me.f0reach.jobs.api.extension.JobRewardSplitter;
import me.f0reach.jobs.api.extension.Split;
import me.f0reach.jobs.api.extension.Transfer;
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

class SplitterChainTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private PipelineContext ctx(double finalReward) {
        Player player = server.addPlayer();
        MatchCriteria criteria = new MatchCriteria.EntityKilled(
                new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(
                ActionType.ENTITY_KILLED, criteria, new RewardAmount.Fixed(1.0), null,
                new ActionKey("kill:minecraft:zombie"));
        JobDefinition job = new JobDefinition(
                new JobId("combat"), "Combat", NamespacedKey.minecraft("iron_sword"),
                List.of(entry), VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty()
        );
        DetectedAction action = new DetectedAction(
                player, job.id(), entry, entry.derivedKey(), 1, SourceFlags.none(), DetectionSubject.empty()
        );
        PipelineContext c = new PipelineContext(action, job, Instant.now());
        c.setFinalReward(finalReward);
        return c;
    }

    private JobRewardSplitter deducting(String id, int priority, double amount) {
        return new JobRewardSplitter() {
            @Override public Split split(JobRewardSplitContext c) {
                return new Split(amount, List.of(new Transfer("company", amount, id)));
            }
            @Override public int getPriority() { return priority; }
            @Override public String getId() { return id; }
        };
    }

    @Test
    void chainedSplittersReduceNetPaid() {
        SplitterChain chain = new SplitterChain(plugin);
        chain.register(deducting("a", 10, 20));
        chain.register(deducting("b", 20, 30));
        // 100 - 20 = 80 → 80 - 30 = 50
        assertEquals(50.0, chain.applyAndComputeNet(ctx(100.0)));
    }

    @Test
    void negativeDeductionClampsToZero() {
        SplitterChain chain = new SplitterChain(plugin);
        // Split's constructor validates non-negative; try to break down to 0 via multiple deductions.
        chain.register(deducting("big", 10, 200));
        assertEquals(0.0, chain.applyAndComputeNet(ctx(50.0)));
    }

    @Test
    void throwingSplitterIsSkipped() {
        SplitterChain chain = new SplitterChain(plugin);
        chain.register(new JobRewardSplitter() {
            @Override public Split split(JobRewardSplitContext c) { throw new RuntimeException("boom"); }
            @Override public int getPriority() { return 10; }
            @Override public String getId() { return "bad"; }
        });
        chain.register(deducting("good", 20, 10));
        assertEquals(90.0, chain.applyAndComputeNet(ctx(100.0)));
    }
}
