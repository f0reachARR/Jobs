package me.f0reach.jobs.detection;

import me.f0reach.jobs.domain.job.ActionKey;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.RewardEntry;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * listener の matcher で確定した「マッチ結果 + 副次情報」を pipeline に渡すための型。
 *
 * <p>spec/03-action-detection.md 「派生キー」および docs/plan/class-structure.md 「detection」を参照。
 */
public record DetectedAction(
        Player player,
        JobId matchedJobId,
        RewardEntry matchedEntry,
        ActionKey derivedKey,
        int amount,
        SourceFlags sourceFlags
) {
    public DetectedAction {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(matchedJobId, "matchedJobId");
        Objects.requireNonNull(matchedEntry, "matchedEntry");
        Objects.requireNonNull(derivedKey, "derivedKey");
        if (amount < 0) throw new IllegalArgumentException("amount must be >= 0");
        if (sourceFlags == null) sourceFlags = SourceFlags.none();
    }
}
