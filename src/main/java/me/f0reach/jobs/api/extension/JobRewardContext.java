package me.f0reach.jobs.api.extension;

import org.bukkit.entity.Player;

/**
 * {@link JobRewardModifier} の入力。
 * spec/06-public-api.md 「JobRewardModifier」を参照。
 */
public interface JobRewardContext {

    Player getPlayer();

    String getJobId();

    String getActionKey();

    /** ここまでの段階で確定している報酬 (未丸め)。 */
    double getCurrentReward();

    boolean isRareHit();
}
