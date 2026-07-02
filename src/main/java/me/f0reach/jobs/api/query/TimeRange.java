package me.f0reach.jobs.api.query;

import java.time.Instant;
import java.util.Objects;

/**
 * 時間範囲 [from, to)。
 *
 * spec/06-public-api.md の TimeRange と同一型として使う。
 */
public record TimeRange(Instant from, Instant to) {
    public TimeRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be <= to");
        }
    }
}
