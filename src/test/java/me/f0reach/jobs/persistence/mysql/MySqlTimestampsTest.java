package me.f0reach.jobs.persistence.mysql;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MySqlTimestampsTest {

    @Test
    void columnTimestampTruncatesToMilliseconds() {
        Instant instant = Instant.parse("2026-07-10T12:34:56.123789Z");

        assertEquals(
                Instant.parse("2026-07-10T12:34:56.123Z"),
                MySqlTimestamps.toColumnTimestamp(instant).toInstant()
        );
    }

    @Test
    void exclusiveBoundTimestampCeilsSubMillisecondInstants() {
        Instant instant = Instant.parse("2026-07-10T12:34:56.123001Z");

        assertEquals(
                Instant.parse("2026-07-10T12:34:56.124Z"),
                MySqlTimestamps.toExclusiveBoundTimestamp(instant).toInstant()
        );
    }

    @Test
    void exclusiveBoundTimestampKeepsMillisecondAlignedInstants() {
        Instant instant = Instant.parse("2026-07-10T12:34:56.123Z");

        assertEquals(instant, MySqlTimestamps.toExclusiveBoundTimestamp(instant).toInstant());
    }
}
