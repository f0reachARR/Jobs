package me.f0reach.jobs.persistence.mysql;

import me.f0reach.jobs.persistence.DailyRewardTotalRepository;
import me.f0reach.jobs.persistence.dto.DailyRewardDelta;
import me.f0reach.jobs.util.UuidBytes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DailyRewardTotalRepository の MySQL 実装。
 * spec/05-persistence.md 「daily_reward_total」を参照。
 */
public final class MySqlDailyRewardTotalRepository implements DailyRewardTotalRepository {

    private static final String SQL_GET = """
            SELECT total_reward
            FROM daily_reward_total
            WHERE player_uuid = ? AND reward_date = ?
            """;

    private static final String SQL_UPSERT = """
            INSERT INTO daily_reward_total (player_uuid, reward_date, total_reward)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE total_reward = total_reward + VALUES(total_reward)
            """;

    private final DataSource dataSource;

    public MySqlDailyRewardTotalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public double getTotal(UUID player, LocalDate date) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_GET)) {
            ps.setBytes(1, UuidBytes.toBytes(player));
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0.0;
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("getTotal failed for " + player + " on " + date, e);
        }
    }

    @Override
    public void addBatch(List<DailyRewardDelta> deltas) {
        if (deltas.isEmpty()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {
            for (DailyRewardDelta delta : deltas) {
                ps.setBytes(1, UuidBytes.toBytes(delta.playerUuid()));
                ps.setDate(2, Date.valueOf(delta.rewardDate()));
                ps.setBigDecimal(3, java.math.BigDecimal.valueOf(delta.deltaReward()));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("addBatch failed (" + deltas.size() + " deltas)", e);
        }
    }
}
