package me.f0reach.jobs.modifier;

import me.f0reach.jobs.api.extension.JobRewardContext;
import me.f0reach.jobs.api.extension.JobRewardModifier;
import me.f0reach.jobs.api.extension.ModifiedReward;
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

class ExtensionModifierChainTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private PipelineContext ctx(double initialFinal) {
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
        c.setBaseReward(initialFinal);
        c.setFinalReward(initialFinal);
        return c;
    }

    private JobRewardModifier multiplier(String id, int priority, double factor) {
        return new JobRewardModifier() {
            @Override public ModifiedReward modify(JobRewardContext c) {
                return new ModifiedReward(c.getCurrentReward() * factor, id);
            }
            @Override public int getPriority() { return priority; }
            @Override public String getId() { return id; }
        };
    }

    @Test
    void appliesInPriorityOrder() {
        ExtensionModifierChain chain = new ExtensionModifierChain(plugin);
        chain.register(multiplier("double", 100, 2.0));
        chain.register(multiplier("plus_ten_percent", 50, 1.1));
        // priority=50 が先 → 100 * 1.1 = 110 → * 2.0 = 220
        assertEquals(220.0, chain.apply(ctx(100.0)), 1e-9);
    }

    @Test
    void registerSameIdReplacesExisting() {
        ExtensionModifierChain chain = new ExtensionModifierChain(plugin);
        chain.register(multiplier("m", 10, 2.0));
        chain.register(multiplier("m", 10, 3.0)); // 同 id で上書き
        assertEquals(30.0, chain.apply(ctx(10.0)));
    }

    @Test
    void unregisterRemovesModifier() {
        ExtensionModifierChain chain = new ExtensionModifierChain(plugin);
        chain.register(multiplier("m", 10, 2.0));
        chain.unregister("m");
        assertEquals(10.0, chain.apply(ctx(10.0)));
    }

    @Test
    void throwingModifierIsSkippedButChainContinues() {
        ExtensionModifierChain chain = new ExtensionModifierChain(plugin);
        chain.register(new JobRewardModifier() {
            @Override public ModifiedReward modify(JobRewardContext c) { throw new RuntimeException("boom"); }
            @Override public int getPriority() { return 10; }
            @Override public String getId() { return "bad"; }
        });
        chain.register(multiplier("good", 20, 2.0));
        // bad は skip、good は残額に *2
        assertEquals(20.0, chain.apply(ctx(10.0)));
    }
}
