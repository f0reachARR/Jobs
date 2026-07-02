package me.f0reach.jobs.domain.job;

import java.util.List;

/**
 * ジョブごとの単調性ペナルティ設定。
 * 詳細は spec/02-yaml-schema.md 「variety_penalty」を参照。
 */
public record VarietyPenaltyConfig(
        boolean enabled,
        int window,
        List<CurvePoint> curve,
        String disclosedMessage,
        boolean hideNumbers
) {
    public VarietyPenaltyConfig {
        if (enabled) {
            if (window <= 0) throw new IllegalArgumentException("window must be > 0");
            if (curve == null || curve.isEmpty()) {
                throw new IllegalArgumentException("curve must not be empty when enabled");
            }
        }
        curve = curve == null ? List.of() : List.copyOf(curve);
    }

    public static VarietyPenaltyConfig disabled() {
        return new VarietyPenaltyConfig(false, 0, List.of(), null, false);
    }

    /**
     * curve の 1 点。up_to は上限値 (その値以下)、multiplier は当該レンジの倍率。
     */
    public record CurvePoint(double upTo, double multiplier) {
        public CurvePoint {
            if (multiplier < 0.0) throw new IllegalArgumentException("multiplier must be >= 0");
        }
    }
}
