package me.f0reach.jobs.persistence.mysql;

import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import me.f0reach.jobs.util.UuidBytes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * PlayerJobRepository の MySQL 実装。
 * spec/05-persistence.md 「player_job」の PreparedStatement を素直に書く。
 */
public final class MySqlPlayerJobRepository implements PlayerJobRepository {

    private static final String SQL_FIND_CURRENT = """
            SELECT job_id, selected_at
            FROM player_job
            WHERE player_uuid = ?
            ORDER BY selected_at DESC
            LIMIT 1
            """;

    private static final String SQL_INSERT = """
            INSERT INTO player_job (player_uuid, job_id, selected_at)
            VALUES (?, ?, ?)
            """;

    private static final String SQL_LAST_CHANGED = """
            SELECT selected_at
            FROM player_job
            WHERE player_uuid = ?
            ORDER BY selected_at DESC
            LIMIT 1
            """;

    private final DataSource dataSource;

    public MySqlPlayerJobRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PlayerJobRow> findCurrent(UUID player) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_CURRENT)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String jobId = rs.getString(1);
                Instant selectedAt = rs.getTimestamp(2).toInstant();
                return Optional.of(new PlayerJobRow(player, jobId, selectedAt));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findCurrent failed for " + player, e);
        }
    }

    @Override
    public void insertSelection(UUID player, String jobId, Instant selectedAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            ps.setString(2, jobId);
            ps.setTimestamp(3, Timestamp.from(selectedAt));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insertSelection failed for " + player, e);
        }
    }

    @Override
    public Optional<Instant> lastChangedAt(UUID player) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LAST_CHANGED)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getTimestamp(1).toInstant());
            }
        } catch (SQLException e) {
            throw new RuntimeException("lastChangedAt failed for " + player, e);
        }
    }
}
