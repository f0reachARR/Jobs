package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.DetectionSubject;
import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;

/**
 * entity_bred: 交配イベント。
 *
 * <p>getBreeder が Player でない case は Phase 7 の BreedNonPlayerBreederCheck で 0 確定するが、
 * まず「player でない場合は dispatch しない」で足りる。Phase 7 で 0 ログを残す要件が出た場合は
 * 「非 Player breeder として 0 dispatch」に切り替える。
 */
public final class BreedListener implements Listener {

    private final EventDispatcher dispatcher;

    public BreedListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        MatchContext ctx = MatchContext.builder()
                .entity(event.getEntity().getType().getKey())
                .amount(1)
                .build();
        DetectionSubject subject = DetectionSubject.builder()
                .breederIsPlayer(true)
                .build();
        dispatcher.dispatch(player, ActionType.ENTITY_BRED, ctx, SourceFlags.none(), subject);
    }
}
