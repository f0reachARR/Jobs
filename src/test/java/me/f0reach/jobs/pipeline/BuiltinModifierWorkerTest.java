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
import me.f0reach.jobs.modifier.dailycap.DailyCapEvaluator;
import me.f0reach.jobs.modifier.dailycap.DailyTotalView;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.dto.ActionLogRow;
import me.f0reach.jobs.pipeline.async.RewardWorkQueue;
import me.f0reach.jobs.pipeline.async.RewardWorker;
import me.f0reach.jobs.pipeline.async.WorkerRewardDispatcher;
import me.f0reach.jobs.pipeline.stage.BuiltinModifierStage;
import me.f0reach.jobs.testsupport.InlineRewardDispatcher;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内蔵 Modifier をワーカースレッド経由で流したときの整合性を検証する。
 * docs/plan/async-reward-pipeline.md 「実行モデル」「daily_cap の日付の起点」を参照。
 */
class BuiltinModifierWorkerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private static Logger silentLogger() {
        Logger logger = Logger.getLogger("BuiltinModifierWorkerTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
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

    private PipelineContext ctx(Player player, JobDefinition job, Instant occurredAt, double reward) {
        DetectedAction action = new DetectedAction(
                player, job.id(), job.rewards().get(0),
                job.rewards().get(0).derivedKey(), 1, SourceFlags.none()
        );
        PipelineContext c = new PipelineContext(action, job, occurredAt);
        c.setBaseReward(reward);
        c.setFinalReward(reward);
        return c;
    }

    private BuiltinModifierStage stage(DailyTotalView totals, long cap) {
        VarietyPenaltyEvaluator variety = new VarietyPenaltyEvaluator(
                plugin, new StubActionLogRepo(), new me.f0reach.jobs.util.AsyncExecutor(plugin),
                new InlineRewardDispatcher());
        DailyCapEvaluator capEval = new DailyCapEvaluator(
                totals,
                new PluginConfig.DailyCapConfig(cap, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL));
        return new BuiltinModifierStage(variety, capEval, ZONE);
    }

    @Test
    void serialWorkerKeepsTheDailyTotalExactAndNeverOverpaysTheCap() throws Exception {
        JobDefinition job = job();
        Player player = server.addPlayer();
        DateKeyedTotals totals = new DateKeyedTotals();
        BuiltinModifierStage stage = stage(totals, 1_000);

        Logger logger = silentLogger();
        RewardWorkQueue queue = new RewardWorkQueue(logger, 10_000, List.of());
        RewardWorker worker = new RewardWorker(logger, queue);
        WorkerRewardDispatcher dispatcher = new WorkerRewardDispatcher(queue);
        worker.start();

        // 1 件 10.0 を 200 件。cap 1000 なので 100 件で打ち止めになる。
        Instant at = Instant.parse("2026-08-03T03:00:00Z");
        for (int i = 0; i < 200; i++) {
            PipelineContext c = ctx(player, job, at, 10.0);
            dispatcher.dispatchReward(() -> stage.execute(c));
        }
        worker.drainAndStop(10_000);

        LocalDate date = at.atZone(ZONE).toLocalDate();
        assertEquals(1_000.0, totals.totalOn(player.getUniqueId(), date), 1e-9,
                "単一ワーカーで直列化されるので read-modify-write が失われない");
        assertEquals(200, worker.processedTotal());
    }

    @Test
    void varietyPenaltyThroughTheWorkerMatchesSynchronousExecution() throws Exception {
        VarietyPenaltyConfig variety = new VarietyPenaltyConfig(
                true, 5, List.of(
                        new VarietyPenaltyConfig.CurvePoint(0.5, 1.0),
                        new VarietyPenaltyConfig.CurvePoint(1.01, 0.2)
                ),
                "monotonous work reduces rewards", false
        );
        MatchCriteria c = new MatchCriteria.EntityKilled(
                new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(
                ActionType.ENTITY_KILLED, c, new RewardAmount.Fixed(10.0), null,
                new ActionKey("kill:minecraft:zombie"));
        JobDefinition job = new JobDefinition(
                new JobId("combat"), "Combat", null, NamespacedKey.minecraft("iron_sword"),
                List.of(entry), variety, AntiAutomationConfig.empty());

        Instant at = Instant.parse("2026-08-03T03:00:00Z");
        int actions = 12;

        // 同期実行の期待値を先に取る。
        Player syncPlayer = server.addPlayer();
        BuiltinModifierStage syncStage = stage(new DateKeyedTotals(), 0);
        List<Double> expected = new java.util.ArrayList<>();
        for (int i = 0; i < actions; i++) {
            PipelineContext ctx = ctx(syncPlayer, job, at, 10.0);
            syncStage.execute(ctx);
            expected.add(ctx.finalReward());
        }

        // 同じ列をワーカー経由で流す。
        Player workerPlayer = server.addPlayer();
        BuiltinModifierStage workerStage = stage(new DateKeyedTotals(), 0);
        Logger logger = silentLogger();
        RewardWorkQueue queue = new RewardWorkQueue(logger, 1_000, List.of());
        RewardWorker worker = new RewardWorker(logger, queue);
        WorkerRewardDispatcher dispatcher = new WorkerRewardDispatcher(queue);
        List<PipelineContext> contexts = new java.util.ArrayList<>();
        for (int i = 0; i < actions; i++) {
            PipelineContext ctx = ctx(workerPlayer, job, at, 10.0);
            contexts.add(ctx);
            dispatcher.dispatchReward(() -> workerStage.execute(ctx));
        }
        worker.start();
        worker.drainAndStop(10_000);

        for (int i = 0; i < actions; i++) {
            assertEquals(expected.get(i), contexts.get(i).finalReward(), 1e-9,
                    "action " + i + " の倍率がワーカー経由でも一致する");
        }
        // window=5 が埋まった 6 件目以降はペナルティが乗る。
        assertEquals(10.0, expected.get(4), 1e-9);
        assertEquals(2.0, expected.get(5), 1e-9);
    }

    @Test
    void dailyCapUsesTheOccurrenceDateNotTheProcessingDate() {
        JobDefinition job = job();
        Player player = server.addPlayer();
        DateKeyedTotals totals = new DateKeyedTotals();
        BuiltinModifierStage stage = stage(totals, 1_000);

        // Asia/Tokyo で 8/3 23:59 と 8/4 00:01 にあたる 2 件。
        Instant lateOn3rd = Instant.parse("2026-08-03T14:59:00Z");
        Instant earlyOn4th = Instant.parse("2026-08-03T15:01:00Z");
        assertEquals(LocalDate.of(2026, 8, 3), lateOn3rd.atZone(ZONE).toLocalDate());
        assertEquals(LocalDate.of(2026, 8, 4), earlyOn4th.atZone(ZONE).toLocalDate());

        stage.execute(ctx(player, job, lateOn3rd, 600.0));
        stage.execute(ctx(player, job, earlyOn4th, 600.0));

        // 日付ごとに別枠なので、どちらも cap 1000 に収まり満額支払われる。
        assertEquals(600.0, totals.totalOn(player.getUniqueId(), LocalDate.of(2026, 8, 3)), 1e-9);
        assertEquals(600.0, totals.totalOn(player.getUniqueId(), LocalDate.of(2026, 8, 4)), 1e-9);
    }

    @Test
    void capTrimsTheSecondActionWithinTheSameDate() {
        JobDefinition job = job();
        Player player = server.addPlayer();
        DateKeyedTotals totals = new DateKeyedTotals();
        BuiltinModifierStage stage = stage(totals, 1_000);

        Instant at = Instant.parse("2026-08-03T03:00:00Z");
        PipelineContext first = ctx(player, job, at, 600.0);
        PipelineContext second = ctx(player, job, at, 600.0);
        stage.execute(first);
        stage.execute(second);

        assertEquals(600.0, first.finalReward(), 1e-9);
        assertEquals(400.0, second.finalReward(), 1e-9);
        assertTrue(second.zeroReasons().contains("daily_cap_hit"));
    }

    /** (player, date) で分けて累計を持つ stub。 */
    private static final class DateKeyedTotals implements DailyTotalView {
        private final Map<String, Double> totals = new ConcurrentHashMap<>();

        private static String key(UUID p, LocalDate d) { return p + "@" + d; }

        @Override
        public double totalOn(UUID playerUuid, LocalDate date) {
            return totals.getOrDefault(key(playerUuid, date), 0.0);
        }

        @Override
        public double forJobOn(UUID playerUuid, LocalDate date, String jobId) {
            return totalOn(playerUuid, date);
        }

        @Override
        public void add(UUID playerUuid, LocalDate date, String jobId, double amount) {
            totals.merge(key(playerUuid, date), amount, Double::sum);
        }
    }

    /** recentKeys だけ空を返せばよい stub。 */
    private static final class StubActionLogRepo implements ActionLogRepository {
        @Override public void insertBatch(List<ActionLogRow> rows) {}
        @Override public double sumReward(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return 0; }
        @Override public long countActions(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return 0; }
        @Override public java.util.Set<String> distinctKeys(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return java.util.Set.of(); }
        @Override public int continuousStreakSec(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return 0; }
        @Override public double maxUnitPrice(UUID p, me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return 0; }
        @Override public java.util.Set<UUID> distinctActors(me.f0reach.jobs.api.query.ActionFilter f, me.f0reach.jobs.api.query.TimeRange r) { return java.util.Set.of(); }
        @Override public int deleteOlderThan(Instant cutoff) { return 0; }
        @Override public List<String> recentKeys(UUID player, String jobId, int limit) { return List.of(); }
        @Override public Map<String, Double> sumRewardByJob(UUID p, me.f0reach.jobs.api.query.TimeRange r) { return Map.of(); }
        @Override public List<ActionLogRow> recent(UUID p, me.f0reach.jobs.api.query.TimeRange r, int limit) { return List.of(); }
        @Override public RareHitStats rareHitStats(me.f0reach.jobs.api.query.TimeRange r, String jobId) { return new RareHitStats(0L, 0L, 0.0); }
    }
}
