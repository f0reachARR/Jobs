package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * spawner_origin_kills: {@link Entity#getEntitySpawnReason()} が SPAWNER なら 0。
 * spec/04-reward-pipeline.md 「自動化対策」1 番目。
 *
 * <p>Entity ref は {@link me.f0reach.jobs.detection.DetectionSubject#killedEntity()} 経由で受け取る。
 * listener は spawn reason を読まず、この Stage で読む
 * (class-structure.md 「detection.native_」の EntityKilledListener 節)。
 */
public final class SpawnerOriginCheck implements AntiAutomationCheck {

    public static final String REASON = "spawner_origin_kill";

    @Override
    public boolean appliesTo(PipelineContext ctx, ActionType actionType) {
        if (actionType != ActionType.ENTITY_KILLED) return false;
        AntiAutomationConfig cfg = ctx.jobDefinition().antiAutomation();
        return cfg != null && cfg.spawnerOriginKills() == AntiAutomationConfig.SpawnerOriginKills.ZERO;
    }

    @Override
    public String evaluate(PipelineContext ctx) {
        Entity killed = ctx.subject().killedEntity();
        if (killed == null) return null;
        return killed.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER ? REASON : null;
    }
}
