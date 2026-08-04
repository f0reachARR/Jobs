package me.f0reach.jobs.modifier.variety;

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
import me.f0reach.jobs.testsupport.InlineRewardDispatcher;
import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ring buffer の作り直しを検証する。
 * buffer の容量は生成時に固定されるので、{@code /jobs reload} で window が変わったら
 * 作り直さないと古い window のまま評価が続いてしまう。
 */
class VarietyPenaltyEvaluatorTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final JobId JOB = new JobId("mining");

    private Plugin plugin;
    private VarietyPenaltyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
        evaluator = new VarietyPenaltyEvaluator(
                plugin, null, new AsyncExecutor(plugin), new InlineRewardDispatcher());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reusesTheBufferWhileTheWindowStaysTheSame() {
        JobDefinition job = job(4);
        evaluator.evaluateAndRecord(PLAYER, job, "break:minecraft:stone");
        evaluator.evaluateAndRecord(PLAYER, job, "break:minecraft:stone");

        VarietyPenaltyEvaluator.Snapshot snapshot = evaluator.snapshot(PLAYER, JOB);
        assertEquals(4, snapshot.capacity());
        assertEquals(2, snapshot.size(), "同じ window なら記録が積み上がる");
    }

    @Test
    void rebuildsTheBufferWhenTheWindowChanges() {
        evaluator.evaluateAndRecord(PLAYER, job(4), "break:minecraft:stone");
        evaluator.evaluateAndRecord(PLAYER, job(4), "break:minecraft:stone");

        // reload で window が 4 → 8 に変わった状況。
        evaluator.evaluateAndRecord(PLAYER, job(8), "break:minecraft:deepslate");

        VarietyPenaltyEvaluator.Snapshot snapshot = evaluator.snapshot(PLAYER, JOB);
        assertEquals(8, snapshot.capacity(), "新しい window の容量で作り直される");
        assertEquals(1, snapshot.size(), "旧 buffer の履歴は引き継がない");
        assertEquals("break:minecraft:deepslate", snapshot.topKey());
    }

    @Test
    void invalidateBuffersDropsEverything() {
        evaluator.evaluateAndRecord(PLAYER, job(4), "break:minecraft:stone");

        evaluator.invalidateBuffers();

        assertNull(evaluator.snapshot(PLAYER, JOB), "破棄後は未初期化として扱う");
    }

    private static JobDefinition job(int window) {
        RewardEntry entry = new RewardEntry(
                ActionType.BLOCK_BROKEN,
                new MatchCriteria.BlockBroken(
                        new KeyMatcher.Single(NamespacedKey.minecraft("stone")), false, false),
                new RewardAmount.Fixed(1.0),
                null,
                new ActionKey("break:minecraft:stone"));
        VarietyPenaltyConfig variety = new VarietyPenaltyConfig(
                true, window,
                List.of(new VarietyPenaltyConfig.CurvePoint(1.01, 0.5)),
                null, false);
        return new JobDefinition(
                JOB, "Mining", null, NamespacedKey.minecraft("iron_pickaxe"),
                List.of(entry), variety, AntiAutomationConfig.empty());
    }
}
