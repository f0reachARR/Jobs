package me.f0reach.jobs.domain.job;

import java.util.Objects;

/**
 * rewards[] の 1 エントリ。
 *
 * @param actionType   trigger する ActionType（criteria.actionType() と一致）
 * @param criteria     match 条件
 * @param rewardAmount 通常報酬
 * @param rareBonus    低確率ボーナス（省略可、null）
 * @param derivedKey   マッチ条件から生成される派生キー（{@code registry.ActionKeyDeriver} 経由で埋める）
 */
public record RewardEntry(
        ActionType actionType,
        MatchCriteria criteria,
        RewardAmount rewardAmount,
        RareBonus rareBonus,
        ActionKey derivedKey
) {
    public RewardEntry {
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(rewardAmount, "rewardAmount");
        Objects.requireNonNull(derivedKey, "derivedKey");
        if (criteria.actionType() != actionType) {
            throw new IllegalArgumentException(
                    "criteria.actionType() " + criteria.actionType()
                            + " does not match RewardEntry.actionType " + actionType
            );
        }
    }
}
