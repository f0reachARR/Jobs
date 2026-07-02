package me.f0reach.jobs.domain.job;

import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Objects;

/**
 * 1 ジョブ分のイミュータブル定義。
 * YAML から復元され、reload で丸ごと差し替えられる。
 */
public record JobDefinition(
        JobId id,
        String displayName,
        NamespacedKey icon,
        List<RewardEntry> rewards,
        VarietyPenaltyConfig varietyPenalty,
        AntiAutomationConfig antiAutomation
) {
    public JobDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        if (varietyPenalty == null) varietyPenalty = VarietyPenaltyConfig.disabled();
        if (antiAutomation == null) antiAutomation = AntiAutomationConfig.empty();
    }
}
