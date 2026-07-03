package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.Permissions;
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
import me.f0reach.jobs.modifier.dailycap.DailyCapEvaluator;
import me.f0reach.jobs.modifier.dailycap.DailyTotalView;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.pipeline.stage.BuiltinModifierStage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinModifierStageTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private JobDefinition makeJob(VarietyPenaltyConfig variety) {
        MatchCriteria c = new MatchCriteria.EntityKilled(
                new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(
                ActionType.ENTITY_KILLED, c, new RewardAmount.Fixed(10.0), null,
                new ActionKey("kill:minecraft:zombie"));
        return new JobDefinition(
                new JobId("combat"), "Combat", null, NamespacedKey.minecraft("iron_sword"),
                List.of(entry), variety, AntiAutomationConfig.empty()
        );
    }

    private PipelineContext ctx(Player player, JobDefinition job, double proposedFinal) {
        DetectedAction action = new DetectedAction(
                player, job.id(), job.rewards().get(0),
                job.rewards().get(0).derivedKey(), 1, SourceFlags.none()
        );
        PipelineContext c = new PipelineContext(action, job, Instant.now());
        c.setBaseReward(proposedFinal);
        c.setFinalReward(proposedFinal);
        return c;
    }

    @Test
    void varietyCurveReducesFinalRewardOnRepeat() {
        VarietyPenaltyConfig variety = new VarietyPenaltyConfig(
                true, 5, List.of(
                        new VarietyPenaltyConfig.CurvePoint(0.5, 1.0),
                        new VarietyPenaltyConfig.CurvePoint(1.01, 0.2)
                ),
                "monotonous work reduces rewards", false
        );
        JobDefinition job = makeJob(variety);
        Player player = server.addPlayer();

        VarietyPenaltyEvaluator varietyEval = new VarietyPenaltyEvaluator(
                plugin, new StubActionLogRepo(), new me.f0reach.jobs.util.AsyncExecutor(plugin)
        );
        DailyCapEvaluator capEval = new DailyCapEvaluator(
                new NoopDailyTotal(),
                new PluginConfig.DailyCapConfig(0, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        BuiltinModifierStage stage = new BuiltinModifierStage(varietyEval, capEval);

        // 1〜5 回目: buffer が window(=5) 件に満たないため penalty は発動しない。
        // 記録は進み、5 回目終了時点で buffer は満杯。
        for (int i = 0; i < 5; i++) {
            PipelineContext c = ctx(player, job, 10.0);
            stage.execute(c);
            assertEquals(10.0, c.finalReward(), 1e-9,
                    "buffer 未充填の間は penalty 未発動 (i=" + i + ")");
        }

        // 6 回目: buffer が満杯 & 5/5 が同じキー → ratio 1.0 → multiplier 0.2 → 2.0
        PipelineContext c6 = ctx(player, job, 10.0);
        stage.execute(c6);
        assertEquals(2.0, c6.finalReward(), 1e-9);
    }

    @Test
    void varietyDoesNotPenalizeUntilBufferFills() {
        // 短めの window で「同じキーを 2 回叩いた瞬間」の挙動を明示。
        VarietyPenaltyConfig variety = new VarietyPenaltyConfig(
                true, 3, List.of(
                        new VarietyPenaltyConfig.CurvePoint(0.5, 1.0),
                        new VarietyPenaltyConfig.CurvePoint(1.01, 0.1)
                ),
                "monotonous", false
        );
        JobDefinition job = makeJob(variety);
        Player player = server.addPlayer();

        VarietyPenaltyEvaluator varietyEval = new VarietyPenaltyEvaluator(
                plugin, new StubActionLogRepo(), new me.f0reach.jobs.util.AsyncExecutor(plugin)
        );
        DailyCapEvaluator capEval = new DailyCapEvaluator(
                new NoopDailyTotal(),
                new PluginConfig.DailyCapConfig(0, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        BuiltinModifierStage stage = new BuiltinModifierStage(varietyEval, capEval);

        // window=3。 buffer 充填までの 3 回はすべて素通し。
        for (int i = 0; i < 3; i++) {
            PipelineContext c = ctx(player, job, 10.0);
            stage.execute(c);
            assertEquals(10.0, c.finalReward(), 1e-9);
        }
        // 4 回目で初めて curve が効く。3/3 同キー → ratio 1.0 → multiplier 0.1 → 1.0
        PipelineContext c4 = ctx(player, job, 10.0);
        stage.execute(c4);
        assertEquals(1.0, c4.finalReward(), 1e-9);
    }

    @Test
    void dailyCapZerosOutRewardWhenExceeded() {
        VarietyPenaltyConfig variety = VarietyPenaltyConfig.disabled();
        JobDefinition job = makeJob(variety);
        Player player = server.addPlayer();

        VarietyPenaltyEvaluator varietyEval = new VarietyPenaltyEvaluator(
                plugin, new StubActionLogRepo(), new me.f0reach.jobs.util.AsyncExecutor(plugin)
        );
        DailyCapEvaluator capEval = new DailyCapEvaluator(
                new PresetDailyTotal(1000.0),
                new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        BuiltinModifierStage stage = new BuiltinModifierStage(varietyEval, capEval);

        PipelineContext c = ctx(player, job, 10.0);
        stage.execute(c);
        assertEquals(0.0, c.finalReward());
    }

    @Test
    void dailyCapTrimsPartialOverflow() {
        VarietyPenaltyConfig variety = VarietyPenaltyConfig.disabled();
        JobDefinition job = makeJob(variety);
        Player player = server.addPlayer();

        VarietyPenaltyEvaluator varietyEval = new VarietyPenaltyEvaluator(
                plugin, new StubActionLogRepo(), new me.f0reach.jobs.util.AsyncExecutor(plugin)
        );
        PresetDailyTotal total = new PresetDailyTotal(950.0);
        DailyCapEvaluator capEval = new DailyCapEvaluator(
                total,
                new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        BuiltinModifierStage stage = new BuiltinModifierStage(varietyEval, capEval);

        PipelineContext c = ctx(player, job, 100.0);
        stage.execute(c);
        // 950 + 100 → 50 だけ支払われる。
        assertEquals(50.0, c.finalReward(), 1e-9);
        // 支払確定後、累計に足された結果として 1000 になる。
        assertEquals(1000.0, total.todayTotal(player.getUniqueId()));
    }

    @Test
    void bypassVarietyKeepsMultiplierOneButStillRecords() {
        VarietyPenaltyConfig variety = new VarietyPenaltyConfig(
                true, 3, List.of(
                        new VarietyPenaltyConfig.CurvePoint(0.5, 1.0),
                        new VarietyPenaltyConfig.CurvePoint(1.01, 0.1)
                ),
                "monotonous", false
        );
        JobDefinition job = makeJob(variety);
        Player player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BYPASS_VARIETY_PENALTY, true);

        VarietyPenaltyEvaluator varietyEval = new VarietyPenaltyEvaluator(
                plugin, new StubActionLogRepo(), new me.f0reach.jobs.util.AsyncExecutor(plugin)
        );
        DailyCapEvaluator capEval = new DailyCapEvaluator(
                new NoopDailyTotal(),
                new PluginConfig.DailyCapConfig(0, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        BuiltinModifierStage stage = new BuiltinModifierStage(varietyEval, capEval);

        // window=3 を超えて連打しても reward が削られない。
        // 通常なら 4 回目以降 curve が働き 1.0 まで落ちるはず。
        for (int i = 0; i < 5; i++) {
            PipelineContext c = ctx(player, job, 10.0);
            stage.execute(c);
            assertEquals(10.0, c.finalReward(), 1e-9,
                    "bypass 中は penalty 未発動 (i=" + i + ")");
        }
    }

    @Test
    void bypassDailyCapSkipsTrimAndDoesNotRecordPaid() {
        VarietyPenaltyConfig variety = VarietyPenaltyConfig.disabled();
        JobDefinition job = makeJob(variety);
        Player player = server.addPlayer();
        player.addAttachment(plugin, Permissions.BYPASS_DAILY_CAP, true);

        VarietyPenaltyEvaluator varietyEval = new VarietyPenaltyEvaluator(
                plugin, new StubActionLogRepo(), new me.f0reach.jobs.util.AsyncExecutor(plugin)
        );
        PresetDailyTotal total = new PresetDailyTotal(950.0);
        DailyCapEvaluator capEval = new DailyCapEvaluator(
                total,
                new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        BuiltinModifierStage stage = new BuiltinModifierStage(varietyEval, capEval);

        PipelineContext c = ctx(player, job, 100.0);
        stage.execute(c);
        // 通常なら 50 に削られるが、bypass で 100 そのまま。
        assertEquals(100.0, c.finalReward(), 1e-9);
        // 累計にも足されない (950 のまま)。
        assertEquals(950.0, total.todayTotal(player.getUniqueId()));
        assertTrue(!c.zeroReasons().contains("daily_cap_hit"));
    }

    @Test
    void zeroLockedSkipsBothModifiers() {
        VarietyPenaltyConfig variety = new VarietyPenaltyConfig(
                true, 3,
                List.of(new VarietyPenaltyConfig.CurvePoint(1.01, 0.1)),
                "monotonous", false
        );
        JobDefinition job = makeJob(variety);
        Player player = server.addPlayer();

        VarietyPenaltyEvaluator varietyEval = new VarietyPenaltyEvaluator(
                plugin, new StubActionLogRepo(), new me.f0reach.jobs.util.AsyncExecutor(plugin)
        );
        PresetDailyTotal total = new PresetDailyTotal(0.0);
        DailyCapEvaluator capEval = new DailyCapEvaluator(
                total,
                new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        BuiltinModifierStage stage = new BuiltinModifierStage(varietyEval, capEval);

        PipelineContext c = ctx(player, job, 10.0);
        c.lockZero("anti_automation");
        stage.execute(c);
        assertEquals(0.0, c.finalReward());
        // cache には触っていない (0 のまま)
        assertEquals(0.0, total.todayTotal(player.getUniqueId()));
        assertTrue(c.zeroReasons().contains("anti_automation"));
    }

    // --- test helpers ---

    private static final class StubActionLogRepo implements me.f0reach.jobs.persistence.ActionLogRepository {
        @Override
        public void insertBatch(java.util.List<me.f0reach.jobs.persistence.dto.ActionLogRow> rows) {}
        @Override public long countActions(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return 0; }
        @Override public double sumReward(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return 0; }
        @Override public java.util.Set<String> distinctKeys(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return java.util.Set.of(); }
        @Override public int continuousStreakSec(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return 0; }
        @Override public double maxUnitPrice(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return 0; }
        @Override public int deleteOlderThan(Instant cutoff) { return 0; }
        @Override public List<String> recentKeys(UUID player, String jobId, int limit) { return List.of(); }
        @Override public Map<String, Double> sumRewardByJob(UUID p, me.f0reach.jobs.api.query.TimeRange r) { return Map.of(); }
    }

    private static final class NoopDailyTotal implements DailyTotalView {
        @Override public double todayTotal(UUID p) { return 0; }
        @Override public double todayForJob(UUID p, String j) { return 0; }
        @Override public void add(UUID p, String j, double a) {}
    }

    private static final class PresetDailyTotal implements DailyTotalView {
        private final Map<UUID, Double> totals = new HashMap<>();
        private final double initial;
        PresetDailyTotal(double initial) { this.initial = initial; }
        @Override public double todayTotal(UUID p) { return totals.getOrDefault(p, initial); }
        @Override public double todayForJob(UUID p, String j) { return totals.getOrDefault(p, initial); }
        @Override public void add(UUID p, String j, double a) {
            double cur = totals.getOrDefault(p, initial);
            totals.put(p, cur + a);
        }
    }
}
