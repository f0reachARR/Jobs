package me.f0reach.jobs.testsupport;

import me.f0reach.jobs.persistence.PlayerJobHistoryRepository;
import me.f0reach.jobs.persistence.dto.Actor;
import me.f0reach.jobs.persistence.dto.PlayerJobHistoryRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 単体テスト用の {@link PlayerJobHistoryRepository} in-memory 実装。append-only。
 */
public final class InMemoryPlayerJobHistoryRepository implements PlayerJobHistoryRepository {

    public final List<PlayerJobHistoryRow> rows = new ArrayList<>();
    private long nextId = 1;

    @Override
    public void append(UUID player, String jobId, String previousJobId,
                       Instant changedAt, Actor actor, UUID actorUuid) {
        rows.add(new PlayerJobHistoryRow(
                nextId++, player, jobId, previousJobId, changedAt, actor, actorUuid
        ));
    }

    @Override
    public List<PlayerJobHistoryRow> recent(UUID player, int limit) {
        return rows.stream()
                .filter(r -> r.playerUuid().equals(player))
                .sorted(Comparator.comparing(PlayerJobHistoryRow::changedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<Instant> firstSelectedAt(UUID player) {
        return rows.stream()
                .filter(r -> r.playerUuid().equals(player))
                .map(PlayerJobHistoryRow::changedAt)
                .min(Comparator.naturalOrder());
    }
}
