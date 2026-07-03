package me.f0reach.jobs.persistence.mysql;

import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import me.f0reach.jobs.util.UuidBytes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PlayerJobRepository の MySQL 実装。
 * spec/05-persistence.md 「player_job」の PreparedStatement を素直に書く。
 */
public final class MySqlPlayerJobRepository implements PlayerJobRepository {

    private static final String SQL_FIND = """
            SELECT job_id, cooldown_base_at
            FROM player_job
            WHERE player_uuid = ?
            """;

    private static final String SQL_UPSERT = """
            INSERT INTO player_job (player_uuid, job_id, cooldown_base_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
              job_id = VALUES(job_id),
              cooldown_base_at = VALUES(cooldown_base_at)
            """;

    private static final String SQL_RESET_COOLDOWN = """
            UPDATE player_job SET cooldown_base_at = ? WHERE player_uuid = ?
            """;

    private static final String SQL_DELETE = """
            DELETE FROM player_job WHERE player_uuid = ?
            """;

    private static final String SQL_COUNT_BY_JOB = """
            SELECT job_id, COUNT(*) FROM player_job GROUP BY job_id
            """;

    private final DataSource dataSource;

    public MySqlPlayerJobRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PlayerJobRow> find(UUID player) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String jobId = rs.getString(1);
                Instant cooldownBaseAt = rs.getTimestamp(2).toInstant();
                return Optional.of(new PlayerJobRow(player, jobId, cooldownBaseAt));
            }
        } catch (SQLException e) {
            throw new RuntimeException("find failed for " + player, e);
        }
    }

    @Override
    public void upsert(UUID player, String jobId, Instant cooldownBaseAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            ps.setString(2, jobId);
            ps.setTimestamp(3, Timestamp.from(cooldownBaseAt));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("upsert failed for " + player, e);
        }
    }

    @Override
    public void resetCooldownBase(UUID player) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RESET_COOLDOWN)) {
            ps.setTimestamp(1, Timestamp.from(Instant.EPOCH));
            ps.setBytes(2, UuidBytes.toBytes(player));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("resetCooldownBase failed for " + player, e);
        }
    }

    @Override
    public Map<String, Long> countByJob() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_COUNT_BY_JOB)) {
            Map<String, Long> out = new HashMap<>();
            while (rs.next()) {
                out.put(rs.getString(1), rs.getLong(2));
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("countByJob failed", e);
        }
    }

    @Override
    public void delete(UUID player) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("delete failed for " + player, e);
        }
    }
}
