package me.f0reach.jobs.persistence.mysql;

import me.f0reach.jobs.api.query.ActionFilter;
import me.f0reach.jobs.api.query.TimeRange;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.dto.ActionLogRow;
import me.f0reach.jobs.util.UuidBytes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ActionLogRepository の MySQL 実装。
 * spec/05-persistence.md 「action_log」を参照。
 */
public final class MySqlActionLogRepository implements ActionLogRepository {

    private static final String SQL_INSERT = """
            INSERT INTO action_log
              (player_uuid, job_id, action_key, base_reward, final_reward, rare_hit, amount, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_DELETE_OLDER_THAN = """
            DELETE FROM action_log WHERE occurred_at < ?
            """;

    private static final String SQL_RECENT_KEYS = """
            SELECT action_key
            FROM action_log
            WHERE player_uuid = ? AND job_id = ?
            ORDER BY occurred_at DESC
            LIMIT ?
            """;

    private static final String SQL_SUM_BY_JOB = """
            SELECT job_id, COALESCE(SUM(final_reward), 0)
            FROM action_log
            WHERE player_uuid = ? AND occurred_at >= ? AND occurred_at < ?
            GROUP BY job_id
            """;

    private final DataSource dataSource;

    public MySqlActionLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insertBatch(List<ActionLogRow> rows) {
        if (rows.isEmpty()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            for (ActionLogRow row : rows) {
                ps.setBytes(1, UuidBytes.toBytes(row.playerUuid()));
                ps.setString(2, row.jobId());
                ps.setString(3, row.actionKey());
                ps.setBigDecimal(4, java.math.BigDecimal.valueOf(row.baseReward()));
                ps.setBigDecimal(5, java.math.BigDecimal.valueOf(row.finalReward()));
                ps.setBoolean(6, row.rareHit());
                ps.setInt(7, row.amount());
                ps.setTimestamp(8, Timestamp.from(row.occurredAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("insertBatch failed (" + rows.size() + " rows)", e);
        }
    }

    @Override
    public long countActions(UUID player, ActionFilter filter, TimeRange range) {
        FilterBuilder fb = filterClauses(player, filter, range);
        String sql = "SELECT COUNT(*) FROM action_log WHERE " + fb.clause;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            fb.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("countActions failed", e);
        }
    }

    @Override
    public double sumReward(UUID player, ActionFilter filter, TimeRange range) {
        FilterBuilder fb = filterClauses(player, filter, range);
        String sql = "SELECT COALESCE(SUM(final_reward), 0) FROM action_log WHERE " + fb.clause;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            fb.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("sumReward failed", e);
        }
    }

    @Override
    public Set<String> distinctKeys(UUID player, ActionFilter filter, TimeRange range) {
        FilterBuilder fb = filterClauses(player, filter, range);
        String sql = "SELECT DISTINCT action_key FROM action_log WHERE " + fb.clause;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            fb.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> keys = new HashSet<>();
                while (rs.next()) keys.add(rs.getString(1));
                return keys;
            }
        } catch (SQLException e) {
            throw new RuntimeException("distinctKeys failed", e);
        }
    }

    @Override
    public int continuousStreakSec(UUID player, ActionFilter filter, TimeRange range) {
        // 連続稼働時間 (30 秒以上の gap がなく連続で続いた最長時間) を返す。
        // Phase 3 では計算コストが高いので簡易実装：range 内のアクションを走査し
        // 隣接する 2 件の間隔が gap 秒未満なら継続、超えたら切断してリセット。
        // 詳細は spec/06-public-api.md の意味論に依存する。将来調整する。
        final int gapSeconds = 30;
        FilterBuilder fb = filterClauses(player, filter, range);
        String sql = "SELECT occurred_at FROM action_log WHERE " + fb.clause + " ORDER BY occurred_at ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            fb.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                Instant streakStart = null;
                Instant prev = null;
                long best = 0;
                while (rs.next()) {
                    Instant t = rs.getTimestamp(1).toInstant();
                    if (streakStart == null) {
                        streakStart = t;
                    } else if (prev != null && t.getEpochSecond() - prev.getEpochSecond() > gapSeconds) {
                        best = Math.max(best, prev.getEpochSecond() - streakStart.getEpochSecond());
                        streakStart = t;
                    }
                    prev = t;
                }
                if (streakStart != null && prev != null) {
                    best = Math.max(best, prev.getEpochSecond() - streakStart.getEpochSecond());
                }
                return (int) Math.min(best, Integer.MAX_VALUE);
            }
        } catch (SQLException e) {
            throw new RuntimeException("continuousStreakSec failed", e);
        }
    }

    @Override
    public double maxUnitPrice(UUID player, ActionFilter filter, TimeRange range) {
        FilterBuilder fb = filterClauses(player, filter, range);
        String sql = "SELECT COALESCE(MAX(final_reward / GREATEST(amount, 1)), 0) FROM action_log WHERE " + fb.clause;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            fb.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("maxUnitPrice failed", e);
        }
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_OLDER_THAN)) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteOlderThan failed", e);
        }
    }

    @Override
    public Map<String, Double> sumRewardByJob(UUID player, TimeRange range) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SUM_BY_JOB)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            ps.setTimestamp(2, Timestamp.from(range.from()));
            ps.setTimestamp(3, Timestamp.from(range.to()));
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Double> out = new HashMap<>();
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getDouble(2));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("sumRewardByJob failed", e);
        }
    }

    @Override
    public List<String> recentKeys(UUID player, String jobId, int limit) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RECENT_KEYS)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            ps.setString(2, jobId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> keys = new ArrayList<>();
                while (rs.next()) keys.add(rs.getString(1));
                return keys;
            }
        } catch (SQLException e) {
            throw new RuntimeException("recentKeys failed", e);
        }
    }

    private static final class FilterBuilder {
        final String clause;
        final List<Object> params;

        FilterBuilder(String clause, List<Object> params) {
            this.clause = clause;
            this.params = params;
        }

        void bind(PreparedStatement ps) throws SQLException {
            for (int i = 0; i < params.size(); i++) {
                Object v = params.get(i);
                if (v instanceof byte[] bytes) ps.setBytes(i + 1, bytes);
                else if (v instanceof Timestamp ts) ps.setTimestamp(i + 1, ts);
                else if (v instanceof String s) ps.setString(i + 1, s);
                else if (v instanceof Integer n) ps.setInt(i + 1, n);
                else throw new SQLException("Unsupported bind type: " + v.getClass());
            }
        }
    }

    private FilterBuilder filterClauses(UUID player, ActionFilter filter, TimeRange range) {
        List<Object> params = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("player_uuid = ?");
        params.add(UuidBytes.toBytes(player));
        sb.append(" AND occurred_at >= ? AND occurred_at < ?");
        params.add(Timestamp.from(range.from()));
        params.add(Timestamp.from(range.to()));
        if (filter != null) {
            if (filter.jobId() != null) {
                sb.append(" AND job_id = ?");
                params.add(filter.jobId());
            }
            if (filter.actionKey() != null) {
                sb.append(" AND action_key = ?");
                params.add(filter.actionKey());
            }
            if (filter.actionKeyPrefix() != null) {
                sb.append(" AND action_key LIKE ?");
                params.add(filter.actionKeyPrefix() + "%");
            }
        }
        return new FilterBuilder(sb.toString(), params);
    }
}
