package me.f0reach.jobs.testsupport;

import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 単体テスト用の {@link PlayerJobRepository} in-memory 実装。
 * 1 player 1 row + cooldown_base_at を保持する。
 */
public final class InMemoryPlayerJobRepository implements PlayerJobRepository {

    public final Map<UUID, PlayerJobRow> rows = new HashMap<>();

    @Override
    public Optional<PlayerJobRow> find(UUID player) {
        return Optional.ofNullable(rows.get(player));
    }

    @Override
    public void upsert(UUID player, String jobId, Instant cooldownBaseAt) {
        rows.put(player, new PlayerJobRow(player, jobId, cooldownBaseAt));
    }

    @Override
    public void resetCooldownBase(UUID player) {
        PlayerJobRow existing = rows.get(player);
        if (existing != null) {
            rows.put(player, new PlayerJobRow(player, existing.jobId(), Instant.EPOCH));
        }
    }

    @Override
    public void delete(UUID player) {
        rows.remove(player);
    }

    @Override
    public Map<String, Long> countByJob() {
        Map<String, Long> counts = new HashMap<>();
        for (PlayerJobRow row : rows.values()) {
            counts.merge(row.jobId(), 1L, Long::sum);
        }
        return counts;
    }
}
