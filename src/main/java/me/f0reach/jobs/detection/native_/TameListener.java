package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTameEvent;

/**
 * entity_tamed: プレイヤーが手懐けたとき。
 */
public final class TameListener implements Listener {

    private final EventDispatcher dispatcher;

    public TameListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        MatchContext ctx = MatchContext.builder()
                .entity(event.getEntity().getType().getKey())
                .amount(1)
                .build();
        dispatcher.dispatch(player, ActionType.ENTITY_TAMED, ctx, SourceFlags.none());
    }
}
