package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerShearEntityEvent;

/**
 * entity_sheared: プレイヤーの毛刈り。
 */
public final class ShearListener implements Listener {

    private final EventDispatcher dispatcher;

    public ShearListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        MatchContext ctx = MatchContext.builder()
                .entity(event.getEntity().getType().getKey())
                .amount(1)
                .build();
        dispatcher.dispatch(event.getPlayer(), ActionType.ENTITY_SHEARED, ctx, SourceFlags.none());
    }
}
