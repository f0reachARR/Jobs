package me.f0reach.jobs.api.extension;

/**
 * {@link JobRewardSplitter} の入力。
 * {@link JobRewardContext} に「Modifier 適用後の報酬」を加えたもの。
 * spec/06-public-api.md 「JobRewardSplitter」を参照。
 */
public interface JobRewardSplitContext extends JobRewardContext {

    /** Modifier 適用後の報酬 (未丸め)。Splitter chain の先頭では finalReward と等しい。 */
    double getRewardAfterModifiers();
}
