package me.f0reach.jobs.domain.job;

import java.util.random.RandomGenerator;

/**
 * 報酬額。固定値 (Fixed) と一様乱数の範囲 (Range) の 2 種類。
 *
 * <p>値は小数を許容する。丸めはパイプライン末尾の {@code RewardRoundingStage} で行い、
 * ここでは丸めない ({@link me.f0reach.jobs.config.PluginConfig.RewardConfig} を参照)。
 */
public sealed interface RewardAmount {

    /** 1 回のロールで払う金額を返す。 */
    double roll(RandomGenerator random);

    record Fixed(double value) implements RewardAmount {
        public Fixed {
            if (value < 0) throw new IllegalArgumentException("Fixed reward must be non-negative");
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Fixed reward must be finite");
        }

        @Override
        public double roll(RandomGenerator random) {
            return value;
        }
    }

    record Range(double min, double max) implements RewardAmount {
        public Range {
            if (min < 0) throw new IllegalArgumentException("Range.min must be non-negative");
            if (max < min) throw new IllegalArgumentException("Range.max must be >= min");
            if (!Double.isFinite(min) || !Double.isFinite(max)) {
                throw new IllegalArgumentException("Range bounds must be finite");
            }
        }

        @Override
        public double roll(RandomGenerator random) {
            if (min == max) return min;
            return min + random.nextDouble() * (max - min);
        }
    }
}
