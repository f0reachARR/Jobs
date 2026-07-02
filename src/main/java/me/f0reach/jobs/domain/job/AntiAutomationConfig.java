package me.f0reach.jobs.domain.job;

/**
 * ジョブごとの自動化対策の有効化フラグ。
 * 詳細は spec/02-yaml-schema.md 「anti_automation」を参照。
 *
 * <p>各 field は 3 値を取る。
 * <ul>
 *   <li>{@code null}: 未指定。config.yml のデフォルトを引き継ぐ意味。
 *       {@link #merge(AntiAutomationConfig, AntiAutomationConfig) merge} で defaults を採用する。</li>
 *   <li>{@code ZERO} / section (enabled=true): 明示的に有効化。</li>
 *   <li>{@code DISABLED} / section (enabled=false): 明示的に無効化。
 *       config.yml のデフォルトが有効でも、per-job で「使わない」を選べる。</li>
 * </ul>
 */
public record AntiAutomationConfig(
        SpawnerOriginKills spawnerOriginKills,
        UnplantedCropHarvest unplantedCropHarvest,
        RecentlyPlacedBreak recentlyPlacedBreak,
        AutoFedProcessing autoFedProcessing,
        VillagerRepeatTrade villagerRepeatTrade,
        BreedNonPlayerBreeder breedNonPlayerBreeder
) {

    public static AntiAutomationConfig empty() {
        return new AntiAutomationConfig(null, null, null, null, null, null);
    }

    /**
     * config.yml が持つ default 値の上に per-job YAML の override を重ねる。
     * per-job field が null なら default を採用し、non-null ならその値がそのまま勝つ。
     * section 型（{@link RecentlyPlacedBreak} 等）は部分マージしない（record 丸ごとの置換）。
     */
    public static AntiAutomationConfig merge(AntiAutomationConfig defaults, AntiAutomationConfig override) {
        if (defaults == null) defaults = empty();
        if (override == null) return defaults;
        return new AntiAutomationConfig(
                override.spawnerOriginKills   != null ? override.spawnerOriginKills   : defaults.spawnerOriginKills,
                override.unplantedCropHarvest != null ? override.unplantedCropHarvest : defaults.unplantedCropHarvest,
                override.recentlyPlacedBreak  != null ? override.recentlyPlacedBreak  : defaults.recentlyPlacedBreak,
                override.autoFedProcessing    != null ? override.autoFedProcessing    : defaults.autoFedProcessing,
                override.villagerRepeatTrade  != null ? override.villagerRepeatTrade  : defaults.villagerRepeatTrade,
                override.breedNonPlayerBreeder != null ? override.breedNonPlayerBreeder : defaults.breedNonPlayerBreeder
        );
    }

    public enum SpawnerOriginKills { ZERO, DISABLED }
    public enum UnplantedCropHarvest { ZERO, DISABLED }
    public enum BreedNonPlayerBreeder { ZERO, DISABLED }

    public record RecentlyPlacedBreak(int windowSec, boolean enabled) {
        public RecentlyPlacedBreak {
            if (enabled && windowSec <= 0) {
                throw new IllegalArgumentException("window_sec must be > 0 when enabled");
            }
        }

        public static RecentlyPlacedBreak disabled() {
            return new RecentlyPlacedBreak(0, false);
        }
    }

    public record AutoFedProcessing(int operatorTtlSec, boolean enabled) {
        public AutoFedProcessing {
            if (enabled && operatorTtlSec <= 0) {
                throw new IllegalArgumentException("operator_ttl_sec must be > 0 when enabled");
            }
        }

        public static AutoFedProcessing disabled() {
            return new AutoFedProcessing(0, false);
        }
    }

    public record VillagerRepeatTrade(int cooldownSec, boolean enabled) {
        public VillagerRepeatTrade {
            if (enabled && cooldownSec <= 0) {
                throw new IllegalArgumentException("cooldown_sec must be > 0 when enabled");
            }
        }

        public static VillagerRepeatTrade disabled() {
            return new VillagerRepeatTrade(0, false);
        }
    }
}
