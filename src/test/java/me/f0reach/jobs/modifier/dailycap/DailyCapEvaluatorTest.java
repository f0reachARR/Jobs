package me.f0reach.jobs.modifier.dailycap;

import me.f0reach.jobs.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyCapEvaluatorTest {

    private final UUID player = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 8, 3);

    @Test
    void withinCapPassesThrough() {
        DailyTotalView cache = new DailyTotalCacheStub(0.0);
        DailyCapEvaluator eval = new DailyCapEvaluator(
                cache, new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        DailyCapEvaluator.Result r = eval.evaluate(player, today, "combat", 200);
        assertEquals(200, r.paidReward());
        assertEquals(0, r.trimmed());
        assertFalse(r.capHit());
    }

    @Test
    void trimsRewardWhenPartiallyOverCap() {
        DailyTotalView cache = new DailyTotalCacheStub(900);
        DailyCapEvaluator eval = new DailyCapEvaluator(
                cache, new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        DailyCapEvaluator.Result r = eval.evaluate(player, today, "combat", 300);
        // remaining = 100, so paid = 100, trimmed = 200
        assertEquals(100, r.paidReward());
        assertEquals(200, r.trimmed());
        // 部分支払いなので capHit は false（次のアクションでキャップ到達）。
        assertFalse(r.capHit());
    }

    @Test
    void zeroWhenAlreadyAtCap() {
        DailyTotalView cache = new DailyTotalCacheStub(1000);
        DailyCapEvaluator eval = new DailyCapEvaluator(
                cache, new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        DailyCapEvaluator.Result r = eval.evaluate(player, today, "combat", 500);
        assertEquals(0, r.paidReward());
        assertEquals(500, r.trimmed());
        assertTrue(r.capHit());
    }

    @Test
    void nonPositiveCapDisablesTrimming() {
        DailyTotalView cache = new DailyTotalCacheStub(999_999);
        DailyCapEvaluator eval = new DailyCapEvaluator(
                cache, new PluginConfig.DailyCapConfig(0, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        DailyCapEvaluator.Result r = eval.evaluate(player, today, "combat", 300);
        assertEquals(300, r.paidReward());
        assertEquals(0, r.trimmed());
    }

    @Test
    void perJobScopeUsesJobSpecificTotal() {
        DailyTotalCacheStub cache = new DailyTotalCacheStub(0.0);
        cache.forJob("combat", 800);
        DailyCapEvaluator eval = new DailyCapEvaluator(
                cache, new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.PER_JOB)
        );
        DailyCapEvaluator.Result r = eval.evaluate(player, today, "combat", 300);
        // remaining = 200, so paid = 200
        assertEquals(200, r.paidReward());
        assertEquals(100, r.trimmed());
    }

    @Test
    void recordPaidUpdatesCache() {
        DailyTotalCacheStub cache = new DailyTotalCacheStub(0.0);
        DailyCapEvaluator eval = new DailyCapEvaluator(
                cache, new PluginConfig.DailyCapConfig(1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL)
        );
        eval.recordPaid(player, today, "combat", 200);
        assertEquals(200, cache.totalOn(player, today));
    }

    /** テスト用に total / per-job を明示的に注入できる stub。 */
    private static final class DailyTotalCacheStub implements DailyTotalView {
        private double total;
        private final Map<String, Double> perJob = new HashMap<>();

        DailyTotalCacheStub(double initialTotal) {
            this.total = initialTotal;
        }

        void forJob(String jobId, double v) {
            perJob.put(jobId, v);
        }

        @Override
        public double totalOn(UUID playerUuid, LocalDate date) {
            return total;
        }

        @Override
        public double forJobOn(UUID playerUuid, LocalDate date, String jobId) {
            return perJob.getOrDefault(jobId, 0.0);
        }

        @Override
        public void add(UUID playerUuid, LocalDate date, String jobId, double amount) {
            total += amount;
            perJob.merge(jobId, amount, Double::sum);
        }
    }
}
