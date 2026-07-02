package me.f0reach.jobs.api.extension;

import org.jetbrains.annotations.Nullable;

/**
 * {@link JobRewardModifier#modify(JobRewardContext)} の戻り値。
 * spec/06-public-api.md 「JobRewardModifier」を参照。
 *
 * @param reward 丸め前の新しい報酬額。
 * @param tag    ログ用の Modifier 識別タグ (nullable)。
 */
public record ModifiedReward(double reward, @Nullable String tag) {

    /** 報酬をそのまま通すヘルパ。 */
    public static ModifiedReward passthrough(double reward) {
        return new ModifiedReward(reward, null);
    }
}
