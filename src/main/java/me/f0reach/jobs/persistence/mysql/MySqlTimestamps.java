package me.f0reach.jobs.persistence.mysql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * MySQL DATETIME(3) 用の timestamp 変換ヘルパ。
 */
final class MySqlTimestamps {
    private static final ThreadLocal<Calendar> UTC_CALENDAR = ThreadLocal.withInitial(
            () -> Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    );

    private MySqlTimestamps() {}

    static Timestamp toColumnTimestamp(Instant instant) {
        return Timestamp.from(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    static Timestamp toExclusiveBoundTimestamp(Instant instant) {
        return Timestamp.from(ceilToMillis(instant));
    }

    static void setColumnInstant(PreparedStatement ps, int parameterIndex, Instant instant) throws SQLException {
        setTimestamp(ps, parameterIndex, toColumnTimestamp(instant));
    }

    static void setExclusiveBoundInstant(PreparedStatement ps, int parameterIndex, Instant instant) throws SQLException {
        setTimestamp(ps, parameterIndex, toExclusiveBoundTimestamp(instant));
    }

    static void setTimestamp(PreparedStatement ps, int parameterIndex, Timestamp timestamp) throws SQLException {
        ps.setTimestamp(parameterIndex, timestamp, UTC_CALENDAR.get());
    }

    static Instant getInstant(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnIndex, UTC_CALENDAR.get());
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Instant ceilToMillis(Instant instant) {
        Instant truncated = instant.truncatedTo(ChronoUnit.MILLIS);
        return truncated.equals(instant) ? truncated : truncated.plusMillis(1);
    }
}
