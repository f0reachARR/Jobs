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

    double getTotal(UUID player, LocalDate date);

    void addBatch(List<DailyRewardDelta> deltas);

    /**
     * 対象プレイヤーの指定日の集計を 0 にリセットする（{@code /jobs admin reset-daily-cap}）。
     * 実装は該当 row の削除でも UPDATE ... SET total_reward=0 でも可。
     * scope=PER_JOB では次回ログイン warmup で action_log から復元されるため、この経路は
     * オンライン中のみ効果が持続する（08-permissions.md 参照）。
     */
    void reset(UUID player, LocalDate date);
}
