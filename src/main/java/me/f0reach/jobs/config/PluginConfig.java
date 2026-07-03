package me.f0reach.jobs.config;

import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * config.yml 全体のイミュータブル表現。
 * 詳細は spec/02-yaml-schema.md 「グローバル設定 (config.yml)」を参照。
 */
public record PluginConfig(
        SpecialtyModeConfig specialtyMode,
        RewardConfig reward,
        DailyCapConfig dailyCap,
        PersistenceConfig persistence,
        KvsConfig kvs,
        AntiAutomationConfig antiAutomation
) {

    public record SpecialtyModeConfig(
            double rewardNonSpecialty,
            boolean showSelectDialogOnJoin,
            boolean discloseBeforeSelect,
            boolean discloseRewardAmount,
            List<ChangePolicy> changePolicy
    ) {}

    /**
     * 報酬額の丸め設定。ADR-0019 と spec/04-reward-pipeline.md の丸め段階を参照。
     *
     * @param decimals     小数点以下の桁数。0..6 を許容。
     * @param roundingMode {@link java.math.RoundingMode} の名称そのまま。
     */
    public record RewardConfig(
            int decimals,
            RoundingMode roundingMode
    ) {
        public RewardConfig {
            if (decimals < 0 || decimals > 6) {
                throw new IllegalArgumentException("reward.decimals must be in [0, 6]");
            }
            if (roundingMode == null) {
                throw new IllegalArgumentException("reward.rounding_mode is required");
            }
        }
    }

    /**
     * change_policy の 1 エントリ。
     * within が空でかつ isDefault=true のときにフォールバックポリシー。
     */
    public record ChangePolicy(
            boolean isDefault,
            WithinCondition within,
            Duration cooldown
    ) {}

    /**
     * within の条件。Phase 1 では event_hours のみを取り込む。
     * 詳細な意味論は Phase 4 の SpecialtyService で解釈する。
     */
    public record WithinCondition(
            List<Integer> eventHours
    ) {
        public static WithinCondition none() {
            return new WithinCondition(List.of());
        }
    }

    public record DailyCapConfig(
            long amount,
            String resetAt,
            Scope scope
    ) {
        public enum Scope { TOTAL, PER_JOB }
    }

    public record PersistenceConfig(
            Type type,
            String host,
            int port,
            String database,
            String user,
            String password,
            int poolSize,
            int retentionDays
    ) {
        public enum Type { MYSQL }
    }

    public record KvsConfig(
            Type type
    ) {
        public enum Type { MEMORY, REDIS }
    }

    /**
     * 自動化対策のグローバル設定。
     *
     * <p>{@link #defaults} は各ジョブに対する default 値として、per-job YAML の
     * {@code anti_automation} と {@link me.f0reach.jobs.domain.job.AntiAutomationConfig#merge merge}
     * される。per-job YAML でキー未指定なら default が効く。
     *
     * <p>{@link #notifyActionBar} は check の reason 文字列（例：{@code spawner_origin_kill}）
     * から「0 判定時に ActionBar 通知を出すか」への map。key に無いものは通知しない。
     * per-job override は無い（notify はグローバル固定）。
     */
    public record AntiAutomationConfig(
            me.f0reach.jobs.domain.job.AntiAutomationConfig defaults,
            Map<String, Boolean> notifyActionBar
    ) {
        public AntiAutomationConfig {
            if (defaults == null) defaults = me.f0reach.jobs.domain.job.AntiAutomationConfig.empty();
            notifyActionBar = notifyActionBar == null ? Map.of() : Map.copyOf(notifyActionBar);
        }

        public static AntiAutomationConfig empty() {
            return new AntiAutomationConfig(
                    me.f0reach.jobs.domain.job.AntiAutomationConfig.empty(),
                    Map.of());
        }
    }
}
