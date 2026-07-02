package me.f0reach.jobs.modifier.variety;

import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * variety_penalty の curve を昇順に持ち、比率から multiplier を引くヘルパ。
 * spec/02-yaml-schema.md 「variety_penalty」および spec/04-reward-pipeline.md を参照。
 *
 * <p>curve は {@code up_to} の昇順で保持する。{@code lookup(ratio)} は
 * {@code ratio <= up_to} を満たす最初のエントリの {@code multiplier} を返す。
 * どのエントリにも収まらない場合は 1.0 を返す（実質ペナルティなし）。
 */
public final class VarietyCurveLookup {

    private final List<VarietyPenaltyConfig.CurvePoint> sorted;

    public VarietyCurveLookup(List<VarietyPenaltyConfig.CurvePoint> curve) {
        List<VarietyPenaltyConfig.CurvePoint> copy = new ArrayList<>(curve);
        copy.sort(Comparator.comparingDouble(VarietyPenaltyConfig.CurvePoint::upTo));
        this.sorted = List.copyOf(copy);
    }

    /** ratio (0.0..1.0) を渡すと当該レンジの multiplier を返す。 */
    public double lookup(double ratio) {
        for (VarietyPenaltyConfig.CurvePoint point : sorted) {
            if (ratio <= point.upTo()) {
                return point.multiplier();
            }
        }
        return 1.0;
    }

    /** 参照用（テスト・UI）。 */
    public List<VarietyPenaltyConfig.CurvePoint> curve() {
        return sorted;
    }
}
