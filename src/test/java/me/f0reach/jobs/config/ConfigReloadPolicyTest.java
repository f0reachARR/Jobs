package me.f0reach.jobs.config;

import org.junit.jupiter.api.Test;

import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /jobs reload で config.yml のどこを反映し、どこを引き継ぐか。
 * 起動時に組んだ構造物に直結するキーは旧値のままにして、変更をログに出せるよう列挙する。
 */
class ConfigReloadPolicyTest {

    @Test
    void appliesTheKeysThatOnlyNeedRereading() {
        PluginConfig before = config();
        PluginConfig loaded = new PluginConfig(
                new PluginConfig.SpecialtyModeConfig(
                        0.5, false, false, false,
                        List.of(new PluginConfig.ChangePolicy(
                                true, PluginConfig.WithinCondition.none(), Duration.ofDays(1)))),
                new PluginConfig.RewardConfig(3, RoundingMode.FLOOR, async(true, 1000, 200, 999L)),
                new PluginConfig.DailyCapConfig(
                        2000, "04:00", PluginConfig.DailyCapConfig.Scope.TOTAL),
                before.persistence(),
                before.kvs(),
                new PluginConfig.SmeltingConfig(40L, 128),
                new PluginConfig.AntiAutomationConfig(
                        me.f0reach.jobs.domain.job.AntiAutomationConfig.empty(),
                        Map.of("spawner_origin_kill", false)));

        PluginConfig effective = ConfigReloadPolicy.effective(before, loaded);

        assertEquals(loaded.specialtyMode(), effective.specialtyMode());
        assertEquals(3, effective.reward().decimals());
        assertEquals(RoundingMode.FLOOR, effective.reward().roundingMode());
        assertEquals(2000, effective.dailyCap().amount());
        assertEquals("04:00", effective.dailyCap().resetAt());
        assertEquals(loaded.smelting(), effective.smelting());
        assertEquals(loaded.antiAutomation(), effective.antiAutomation());
        assertEquals(999L, effective.reward().async().slowExtensionThresholdMs());
        assertTrue(ConfigReloadPolicy.restartRequiredChanges(before, loaded).isEmpty());
    }

    @Test
    void keepsTheKeysThatNeedARestartAndReportsThem() {
        PluginConfig before = config();
        PluginConfig loaded = new PluginConfig(
                before.specialtyMode(),
                new PluginConfig.RewardConfig(
                        before.reward().decimals(), before.reward().roundingMode(),
                        async(false, 10, 20, before.reward().async().slowExtensionThresholdMs())),
                new PluginConfig.DailyCapConfig(
                        before.dailyCap().amount(), before.dailyCap().resetAt(),
                        PluginConfig.DailyCapConfig.Scope.PER_JOB),
                new PluginConfig.PersistenceConfig(
                        PluginConfig.PersistenceConfig.Type.MYSQL, "other-host", 3306,
                        "jobs", "jobs", "", 8, 30),
                before.kvs(),
                before.smelting(),
                before.antiAutomation());

        PluginConfig effective = ConfigReloadPolicy.effective(before, loaded);

        assertEquals(before.persistence(), effective.persistence(), "接続先は再起動まで変えない");
        assertEquals(PluginConfig.DailyCapConfig.Scope.TOTAL, effective.dailyCap().scope(),
                "scope は DailyTotalCache の持ち方が変わるので引き継ぐ");
        assertTrue(effective.reward().async().enabled(), "ワーカー構成は引き継ぐ");
        assertEquals(1000, effective.reward().async().queueCapacity());
        assertEquals(200, effective.reward().async().mainWorkPerTick());

        assertEquals(
                List.of("persistence", "daily_cap.scope", "reward.async.enabled",
                        "reward.async.queue_capacity", "reward.async.main_work_per_tick"),
                ConfigReloadPolicy.restartRequiredChanges(before, loaded));
    }

    private static PluginConfig config() {
        return new PluginConfig(
                new PluginConfig.SpecialtyModeConfig(
                        0.0, true, true, true,
                        List.of(new PluginConfig.ChangePolicy(
                                true, PluginConfig.WithinCondition.none(), Duration.ofDays(5)))),
                new PluginConfig.RewardConfig(0, RoundingMode.HALF_UP, async(true, 1000, 200, 50L)),
                new PluginConfig.DailyCapConfig(
                        1000, "00:00", PluginConfig.DailyCapConfig.Scope.TOTAL),
                new PluginConfig.PersistenceConfig(
                        PluginConfig.PersistenceConfig.Type.MYSQL, "localhost", 3306,
                        "jobs", "jobs", "", 8, 30),
                new PluginConfig.KvsConfig(PluginConfig.KvsConfig.Type.MEMORY),
                new PluginConfig.SmeltingConfig(20L, 4096),
                PluginConfig.AntiAutomationConfig.empty());
    }

    private static PluginConfig.AsyncConfig async(
            boolean enabled, int queueCapacity, int mainWorkPerTick, long slowThresholdMs) {
        return new PluginConfig.AsyncConfig(
                enabled, queueCapacity, List.of(0.5, 0.8), false, mainWorkPerTick,
                slowThresholdMs, 5_000L);
    }
}
