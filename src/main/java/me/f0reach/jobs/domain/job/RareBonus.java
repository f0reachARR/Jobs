package me.f0reach.jobs.domain.job;

/**
 * 低確率ボーナス。ヒット時は通常報酬を rare 報酬で置き換える。
 *
 * @param chance          発火確率 (0.0..1.0)
 * @param rewardAmount    発火時の報酬 (Fixed または Range)
 * @param announceMessage 発火時にサーバ全体に流すメッセージ。{player} はプレイヤー名に置換される。
 *                        省略時は null。
 */
public record RareBonus(
        double chance,
        RewardAmount rewardAmount,
        String announceMessage
) {
    public RareBonus {
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("rare.chance must be in [0.0, 1.0]");
        }
        if (rewardAmount == null) {
            throw new IllegalArgumentException("rare.reward is required");
        }
    }
}
