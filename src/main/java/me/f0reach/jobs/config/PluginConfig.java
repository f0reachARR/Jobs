package me.f0reach.jobs.config;

import java.time.Duration;
import java.util.List;

/**
 * config.yml 全体のイミュータブル表現。
 * 詳細は spec/02-yaml-schema.md 「グローバル設定 (config.yml)」を参照。
 */
public record PluginConfig(
        SpecialtyModeConfig specialtyMode,
        DailyCapConfig dailyCap,
        PersistenceConfig persistence,
        KvsConfig kvs
) {

    public record SpecialtyModeConfig(
            double rewardNonSpecialty,
            List<ChangePolicy> changePolicy
    ) {}

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
}
