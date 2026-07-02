package me.f0reach.jobs.domain.job;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardAmountTest {

    private RandomGenerator rng() {
        return new SplittableRandom(42L);
    }

    @Test
    void fixedReturnsSameValue() {
        RewardAmount amount = new RewardAmount.Fixed(5.0);
        assertEquals(5.0, amount.roll(rng()));
        assertEquals(5.0, amount.roll(rng()));
    }

    @Test
    void fixedAcceptsFractionalValue() {
        RewardAmount amount = new RewardAmount.Fixed(0.5);
        assertEquals(0.5, amount.roll(rng()));
    }

    @Test
    void rangeReturnsInclusiveRange() {
        RewardAmount amount = new RewardAmount.Range(3.0, 8.0);
        RandomGenerator random = rng();
        for (int i = 0; i < 100; i++) {
            double value = amount.roll(random);
            assertTrue(value >= 3.0 && value <= 8.0,
                    "expected [3,8] but got " + value);
        }
    }

    @Test
    void rangeMinEqualsMaxReturnsBoundary() {
        RewardAmount amount = new RewardAmount.Range(7.0, 7.0);
        assertEquals(7.0, amount.roll(rng()));
    }

    @Test
    void rangeAcceptsFractionalBounds() {
        RewardAmount amount = new RewardAmount.Range(1.5, 3.5);
        RandomGenerator random = rng();
        for (int i = 0; i < 100; i++) {
            double value = amount.roll(random);
            assertTrue(value >= 1.5 && value <= 3.5,
                    "expected [1.5,3.5] but got " + value);
        }
    }

    @Test
    void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Fixed(-1.0));
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Range(-1.0, 5.0));
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Range(5.0, 3.0));
    }

    @Test
    void rejectsNonFinite() {
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Fixed(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Fixed(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new RewardAmount.Range(0.0, Double.NaN));
    }
}
