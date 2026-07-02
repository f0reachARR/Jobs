package me.f0reach.jobs.persistence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * action_log テーブルの 1 行に対応する DTO。
 * spec/05-persistence.md 「action_log」を参照。
 *
 * <p>この型は persistence 層の物理レイヤ寄りのため api には露出しない。
 * 公開する集計クエリは {@code api.query.ActionLogQueryService} 側で別途型を持つ。
 */
public record ActionLogRow(
        UUID playerUuid,
        String jobId,
        String actionKey,
        double baseReward,
        double finalReward,
        boolean rareHit,
        int amount,
        Instant occurredAt
) {}
