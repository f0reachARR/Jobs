package me.f0reach.jobs.persistence.mysql;

import me.f0reach.jobs.persistence.PlayerJobHistoryRepository;
import me.f0reach.jobs.persistence.dto.Actor;
import me.f0reach.jobs.persistence.dto.PlayerJobHistoryRow;
import me.f0reach.jobs.util.UuidBytes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PlayerJobHistoryRepository の MySQL 実装。
 * spec/05-persistence.md 「player_job_history」の PreparedStatement を素直に書く。
 */
public final class MySqlPlayerJobHistoryRepository implements PlayerJobHistoryRepository {

    private static final String SQL_APPEND = """
            INSERT INTO player_job_history
              (player_uuid, job_id, previous_job_id, changed_at, actor, actor_uuid)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_RECENT = """
            SELECT id, job_id, previous_job_id, changed_at, actor, actor_uuid
            FROM player_job_history
            WHERE player_uuid = ?
            ORDER BY changed_at DESC
            LIMIT ?
            """;

    private static final String SQL_FIRST_SELECTED_AT = """
            SELECT MIN(changed_at) FROM player_job_history WHERE player_uuid = ?
            """;

    private final DataSource dataSource;

    public MySqlPlayerJobHistoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void append(UUID player,
                       String jobId,
                       String previousJobId,
                       Instant changedAt,
                       Actor actor,
                       UUID actorUuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_APPEND)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            ps.setString(2, jobId);
            if (previousJobId == null) ps.setNull(3, java.sql.Types.VARCHAR);
            else ps.setString(3, previousJobId);
            ps.setTimestamp(4, Timestamp.from(changedAt));
            ps.setString(5, actor.dbValue());
            if (actorUuid == null) ps.setNull(6, java.sql.Types.BINARY);
            else ps.setBytes(6, UuidBytes.toBytes(actorUuid));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("append failed for " + player, e);
        }
    }

    @Override
    public List<PlayerJobHistoryRow> recent(UUID player, int limit) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RECENT)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<PlayerJobHistoryRow> rows = new ArrayList<>();
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String jobId = rs.getString(2);
                    String previousJobId = rs.getString(3);
                    Instant changedAt = rs.getTimestamp(4).toInstant();
                    Actor actor = Actor.fromDb(rs.getString(5));
                    byte[] actorBytes = rs.getBytes(6);
                    UUID actorUuid = actorBytes == null ? null : UuidBytes.fromBytes(actorBytes);
                    rows.add(new PlayerJobHistoryRow(
                            id, player, jobId, previousJobId, changedAt, actor, actorUuid
                    ));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException("recent failed for " + player, e);
        }
    }

    @Override
    public Optional<Instant> firstSelectedAt(UUID player) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIRST_SELECTED_AT)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? Optional.empty() : Optional.of(ts.toInstant());
            }
        } catch (SQLException e) {
            throw new RuntimeException("firstSelectedAt failed for " + player, e);
        }
    }
}
