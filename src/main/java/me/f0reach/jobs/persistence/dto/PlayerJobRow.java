package me.f0reach.jobs.persistence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * player_job テーブルの 1 行。
 * spec/05-persistence.md 「player_job」を参照。
 */
public record PlayerJobRow(
        UUID playerUuid,
        String jobId,
        Instant selectedAt
) {}
