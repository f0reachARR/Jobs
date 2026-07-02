package me.f0reach.jobs.pipeline;

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
import me.f0reach.jobs.pipeline.stage.BaseRewardStage;
import me.f0reach.jobs.pipeline.stage.RareRollStage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseRewardStageTest {

    private ServerMock server;

    @BeforeEach
    void setUp() { server = MockBukkit.mock(); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private RandomGenerator rng() {
        return new SplittableRandom(42L);
    }

    private PipelineContext ctx(RewardEntry entry, int amount) {
        Player player = server.addPlayer();
        JobDefinition job = new JobDefinition(
                new JobId("combat"),
                "Combat",
                NamespacedKey.minecraft("iron_sword"),
                List.of(entry),
                VarietyPenaltyConfig.disabled(),
                AntiAutomationConfig.empty()
        );
        DetectedAction action = new DetectedAction(
                player, new JobId("combat"), entry, entry.derivedKey(), amount, SourceFlags.none()
        );
        return new PipelineContext(action, job, Instant.now());
    }

    private RewardEntry fixedEntry(double reward) {
        MatchCriteria c = new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        return new RewardEntry(ActionType.ENTITY_KILLED, c, new RewardAmount.Fixed(reward), null,
                new ActionKey("kill:minecraft:zombie"));
    }

    @Test
    void baseRewardMultipliesByAmount() {
        var entry = fixedEntry(3.0);
        PipelineContext c = ctx(entry, 5);
        new BaseRewardStage(rng()).execute(c);
        assertEquals(15.0, c.baseReward());
        assertEquals(15.0, c.finalReward());
    }

    @Test
    void baseRewardAmountAtLeastOne() {
        var entry = fixedEntry(3.0);
        PipelineContext c = ctx(entry, 0);
        new BaseRewardStage(rng()).execute(c);
        assertEquals(3.0, c.baseReward());
    }

    @Test
    void baseRewardKeepsFractional() {
        var entry = fixedEntry(0.5);
        PipelineContext c = ctx(entry, 3);
        new BaseRewardStage(rng()).execute(c);
        assertEquals(1.5, c.baseReward());
        assertEquals(1.5, c.finalReward());
    }

    @Test
    void zeroLockedSkipsBaseReward() {
        var entry = fixedEntry(3.0);
        PipelineContext c = ctx(entry, 5);
        c.lockZero("test");
        new BaseRewardStage(rng()).execute(c);
        assertEquals(0.0, c.baseReward());
        assertEquals(0.0, c.finalReward());
    }

    @Test
    void rareRollHitReplacesBaseReward() {
        var criteria = new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("skeleton")));
        var rare = new me.f0reach.jobs.domain.job.RareBonus(
                1.0, new RewardAmount.Fixed(100000.0), "test"
        );
        var entry = new RewardEntry(ActionType.ENTITY_KILLED, criteria,
                new RewardAmount.Fixed(5.0), rare, new ActionKey("kill:minecraft:skeleton"));
        PipelineContext c = ctx(entry, 1);
        new BaseRewardStage(rng()).execute(c);
        assertEquals(5.0, c.finalReward());
        new RareRollStage(rng()).execute(c);
        assertTrue(c.rareHit());
        assertEquals(100000.0, c.finalReward());
    }

    @Test
    void rareRollMissKeepsBaseReward() {
        var criteria = new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("skeleton")));
        var rare = new me.f0reach.jobs.domain.job.RareBonus(
                0.0, new RewardAmount.Fixed(100000.0), null
        );
        var entry = new RewardEntry(ActionType.ENTITY_KILLED, criteria,
                new RewardAmount.Fixed(5.0), rare, new ActionKey("kill:minecraft:skeleton"));
        PipelineContext c = ctx(entry, 1);
        new BaseRewardStage(rng()).execute(c);
        new RareRollStage(rng()).execute(c);
        assertFalse(c.rareHit());
        assertEquals(5.0, c.finalReward());
    }
}
