package me.f0reach.jobs.persistence.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * daily_reward_total への 1 件の差分更新。
 * ActionLogWriteQueue と同じバッチで DailyRewardTotalRepository#addBatch に流れる。
 */
public record DailyRewardDelta(
        UUID playerUuid,
        LocalDate rewardDate,
        long deltaReward
) {}
