package me.f0reach.jobs.persistence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * player_job テーブルの 1 行。1 player = 1 row。
 * spec/05-persistence.md 「player_job」を参照。
 *
 * <p>{@code cooldownBaseAt} はクールダウン計算の起点となる時刻。通常は最終選択時刻、
 * {@code /jobs admin reset-cooldown} で {@link Instant#EPOCH} に上書きされる。
 */
public record PlayerJobRow(
        UUID playerUuid,
        String jobId,
        Instant cooldownBaseAt
) {}
