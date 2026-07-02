package me.f0reach.jobs.persistence;

import me.f0reach.jobs.persistence.dto.PlayerJobRow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * player_job テーブルへのアクセス interface。
 * spec/05-persistence.md 「player_job」に対応。
 *
 * すべての呼び出しは Bukkit main thread から可（JDBC のネットワーク I/O は数回程度の呼び出しなら
 * 許容範囲だが、負荷が問題になれば AsyncExecutor 経由に切り替える余地を残す。threading.md 参照）。
 */
public interface PlayerJobRepository {

    Optional<PlayerJobRow> findCurrent(UUID player);

    void insertSelection(UUID player, String jobId, Instant selectedAt);

    Optional<Instant> lastChangedAt(UUID player);
}
