package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.DetectionSubject;
import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.Dimension;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * entity_killed: killer が Player の EntityDeathEvent。
 * spec/03-action-detection.md 「Track A」を参照。
 */
public final class EntityKilledListener implements Listener {

    private final EventDispatcher dispatcher;

    public EntityKilledListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        MatchContext ctx = MatchContext.builder()
                .entity(event.getEntityType().getKey())
                .amount(1)
                .dimension(Dimension.fromEnvironment(event.getEntity().getWorld().getEnvironment()))
                .build();
        DetectionSubject subject = DetectionSubject.builder()
                .killedEntity(event.getEntity())
                .build();
        dispatcher.dispatch(killer, ActionType.ENTITY_KILLED, ctx, SourceFlags.none(), subject);
    }
}
