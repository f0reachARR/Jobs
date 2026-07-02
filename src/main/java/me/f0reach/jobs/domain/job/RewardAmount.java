package me.f0reach.jobs.domain.job;

import java.util.random.RandomGenerator;

/**
 * 報酬額。固定値 (Fixed) と一様乱数の範囲 (Range) の 2 種類。
 */
public sealed interface RewardAmount {

    /** 1 回のロールで払う金額を返す。 */
    int roll(RandomGenerator random);

    record Fixed(int value) implements RewardAmount {
        public Fixed {
            if (value < 0) throw new IllegalArgumentException("Fixed reward must be non-negative");
        }

        @Override
        public int roll(RandomGenerator random) {
            return value;
        }
    }

    record Range(int min, int max) implements RewardAmount {
        public Range {
            if (min < 0) throw new IllegalArgumentException("Range.min must be non-negative");
            if (max < min) throw new IllegalArgumentException("Range.max must be >= min");
        }

        @Override
        public int roll(RandomGenerator random) {
            if (min == max) return min;
            return random.nextInt(min, max + 1);
        }
    }
}
