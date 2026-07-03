package me.f0reach.jobs.persistence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * player_job_history テーブルの 1 行。
 * spec/05-persistence.md 「player_job_history」を参照。
 *
 * <p>{@code id} は auto increment。書き込み時は 0 を渡す。
 * {@code previousJobId} は初回選択時は null。
 * {@code actorUuid} は actor=ADMIN 以外で null 可。
 */
public record PlayerJobHistoryRow(
        long id,
        UUID playerUuid,
        String jobId,
        String previousJobId,
        Instant changedAt,
        Actor actor,
        UUID actorUuid
) {}
