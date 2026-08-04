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
     * 報酬額の丸め設定と非同期実行の設定。
     * ADR-0019 と spec/04-reward-pipeline.md の丸め段階、
     * docs/plan/async-reward-pipeline.md を参照。
     *
     * @param decimals     小数点以下の桁数。0..6 を許容。
     * @param roundingMode {@link java.math.RoundingMode} の名称そのまま。
     * @param async        段階 4 以降を専用ワーカーで回す設定。
     */
    public record RewardConfig(
            int decimals,
            RoundingMode roundingMode,
            AsyncConfig async
    ) {
        public RewardConfig {
            if (decimals < 0 || decimals > 6) {
                throw new IllegalArgumentException("reward.decimals must be in [0, 6]");
            }
            if (roundingMode == null) {
                throw new IllegalArgumentException("reward.rounding_mode is required");
            }
            if (async == null) async = AsyncConfig.defaults();
        }
    }

    /**
     * 報酬パイプラインの非同期実行設定。
     * docs/plan/async-reward-pipeline.md 「config」を参照。
     *
     * @param enabled                  段階 4 から 11 を専用ワーカースレッドで実行するか。
     *                                 false のとき全段階を main thread で同期実行する。
     * @param queueCapacity            ワーカーへの境界キュー容量。溢れた分は捨てる。
     * @param backlogWarnRatios        キュー深度がこの割合を超えたら WARNING を出す。
     * @param economyOnMain            段階 10 の送金を main thread へ投げ返すか。
     * @param mainWorkPerTick          economyOnMain のとき 1 tick に main thread で処理する上限。
     * @param slowExtensionThresholdMs 拡張 chain 1 件がこれを超えたら WARNING を出す。
     * @param drainTimeoutMs           onDisable でワーカーの drain を待つ上限。
     */
    public record AsyncConfig(
            boolean enabled,
            int queueCapacity,
            List<Double> backlogWarnRatios,
            boolean economyOnMain,
            int mainWorkPerTick,
            long slowExtensionThresholdMs,
            long drainTimeoutMs
    ) {
        public AsyncConfig {
            if (queueCapacity <= 0) {
                throw new IllegalArgumentException("reward.async.queue_capacity must be > 0");
            }
            if (mainWorkPerTick <= 0) {
                throw new IllegalArgumentException("reward.async.main_work_per_tick must be > 0");
            }
            if (slowExtensionThresholdMs < 0) {
                throw new IllegalArgumentException("reward.async.slow_extension_threshold_ms must be >= 0");
            }
            if (drainTimeoutMs < 0) {
                throw new IllegalArgumentException("reward.async.drain_timeout_ms must be >= 0");
            }
            backlogWarnRatios = backlogWarnRatios == null ? List.of() : List.copyOf(backlogWarnRatios);
            for (Double ratio : backlogWarnRatios) {
                if (ratio == null || ratio <= 0.0 || ratio > 1.0) {
                    throw new IllegalArgumentException(
                            "reward.async.backlog_warn_ratio entries must be in (0, 1]");
                }
            }
        }

        public static AsyncConfig defaults() {
            return new AsyncConfig(
                    true, 100_000, List.of(0.5, 0.8), false, 200, 50L, 5_000L);
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
     * within の条件。指定されたキーはすべて満たす必要がある (AND)。
     * 実際の判定は {@link me.f0reach.jobs.specialty.CooldownPolicy} が行う。
     *
     * @param eventHours     [start, end] の 2 要素で表す時間帯。end は排他上限。空なら時間帯を問わない。
     * @param firstJoinWithin サーバー初参加からの経過時間の上限。null なら初参加時刻を問わない。
     */
    public record WithinCondition(
            List<Integer> eventHours,
            Duration firstJoinWithin
    ) {
        public WithinCondition {
            eventHours = eventHours == null ? List.of() : List.copyOf(eventHours);
            if (firstJoinWithin != null
                    && (firstJoinWithin.isZero() || firstJoinWithin.isNegative())) {
                throw new IllegalArgumentException("within.first_join_within must be positive");
            }
        }

        public static WithinCondition none() {
            return new WithinCondition(List.of(), null);
        }

        /** 条件が 1 つも指定されていないか。空の within はどのポリシーにもマッチさせない。 */
        public boolean isEmpty() {
            return eventHours.isEmpty() && firstJoinWithin == null;
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
