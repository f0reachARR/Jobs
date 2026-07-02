package me.f0reach.jobs.listener;

import me.f0reach.jobs.api.event.JobSpecialtyChangedEvent;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.registry.JobRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * プレイヤーが専業を切り替えたときに、新しい専業について
 * VarietyPenaltyEvaluator の ring buffer を warmup する。
 * 旧専業の ring buffer はそのまま残しても実害はない（再選択で有効化）。
 */
public final class SpecialtyChangedListener implements Listener {

    private final VarietyPenaltyEvaluator varietyPenaltyEvaluator;
    private final JobRegistry jobRegistry;

    public SpecialtyChangedListener(
            VarietyPenaltyEvaluator varietyPenaltyEvaluator,
            JobRegistry jobRegistry
    ) {
        this.varietyPenaltyEvaluator = varietyPenaltyEvaluator;
        this.jobRegistry = jobRegistry;
    }

    @EventHandler
    public void onSpecialtyChanged(JobSpecialtyChangedEvent event) {
        JobId newJobId;
        try {
            newJobId = new JobId(event.getNewJobId());
        } catch (IllegalArgumentException e) {
            return;
        }
        JobDefinition def = jobRegistry.get(newJobId).orElse(null);
        if (def == null) return;
        if (def.varietyPenalty() == null || !def.varietyPenalty().enabled()) return;
        varietyPenaltyEvaluator.warmup(event.getPlayer().getUniqueId(), newJobId, def.varietyPenalty().window());
    }
}
