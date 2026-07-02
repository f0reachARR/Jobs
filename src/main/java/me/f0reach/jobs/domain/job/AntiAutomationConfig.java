package me.f0reach.jobs.domain.job;

/**
 * ジョブごとの自動化対策の有効化フラグ。
 * 詳細は spec/02-yaml-schema.md 「anti_automation」を参照。
 *
 * `zero` 以外の処理は将来追加する余地として予約されているため、
 * enum 型は Zero だけを持つ。省略時は null。
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

    public enum SpawnerOriginKills { ZERO }
    public enum UnplantedCropHarvest { ZERO }
    public enum BreedNonPlayerBreeder { ZERO }

    public record RecentlyPlacedBreak(int windowSec) {
        public RecentlyPlacedBreak {
            if (windowSec <= 0) throw new IllegalArgumentException("window_sec must be > 0");
        }
    }

    public record AutoFedProcessing(int operatorTtlSec) {
        public AutoFedProcessing {
            if (operatorTtlSec <= 0) throw new IllegalArgumentException("operator_ttl_sec must be > 0");
        }
    }

    public record VillagerRepeatTrade(int cooldownSec) {
        public VillagerRepeatTrade {
            if (cooldownSec <= 0) throw new IllegalArgumentException("cooldown_sec must be > 0");
        }
    }
}
