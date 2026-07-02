package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * block_placed: プレイヤーが置いた block。
 *
 * <p>Phase 5 では単純に dispatch。Phase 7 で PlantedFlagWriter / PlacementRecorder が
 * ここに便乗して KVS / PDC のマーキングを追加する予定 (spec/03-action-detection.md
 * 「自動化対策のマーキング」)。
 */
public final class BlockPlaceListener implements Listener {

    private final EventDispatcher dispatcher;

    public BlockPlaceListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        MatchContext ctx = MatchContext.builder()
                .block(event.getBlockPlaced().getType().getKey())
                .amount(1)
                .build();
        dispatcher.dispatch(event.getPlayer(), ActionType.BLOCK_PLACED, ctx, SourceFlags.none());
    }
}
