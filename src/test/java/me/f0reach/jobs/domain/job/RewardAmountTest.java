package me.f0reach.jobs.domain.job;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardAmountTest {

    private RandomGenerator rng() {
        // 決定的なテストのため fixed seed の Xoshiro を使う。
        return RandomGeneratorFactory.of("Xoshiro256PlusPlus").create(42L);
    }

    @Test
    void fixedReturnsSameValue() {
        RewardAmount amount = new RewardAmount.Fixed(5);
        assertEquals(5, amount.roll(rng()));
        assertEquals(5, amount.roll(rng()));
    }

    @Test
    void rangeReturnsInclusiveRange() {
        RewardAmount amount = new RewardAmount.Range(3, 8);
        RandomGenerator random = rng();
        for (int i = 0; i < 100; i++) {
            int value = amount.roll(random);
            assertTrue(value >= 3 && value <= 8,
                    "expected [3,8] but got " + value);
        }
    }

    @Test
    void rangeMinEqualsMaxReturnsBoundary() {
        RewardAmount amount = new RewardAmount.Range(7, 7);
        assertEquals(7, amount.roll(rng()));
    }

    @Test
    void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Fixed(-1));
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Range(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Range(5, 3));
    }
}
