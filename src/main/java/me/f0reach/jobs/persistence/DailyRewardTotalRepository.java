package me.f0reach.jobs.persistence;

import me.f0reach.jobs.persistence.dto.DailyRewardDelta;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * daily_reward_total テーブルへのアクセス interface。
 * spec/05-persistence.md 「daily_reward_total」を参照。
 */
public interface DailyRewardTotalRepository {

    long getTotal(UUID player, LocalDate date);

    void addBatch(List<DailyRewardDelta> deltas);
}
