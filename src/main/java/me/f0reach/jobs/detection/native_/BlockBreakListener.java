package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * block_broken: プレイヤーが破壊した block を通常経路で流す。
 *
 * <p>via_tnt=true の合成イベントは detection.tnt.TntPrimerTracker が別途組み立てて、
 * SourceFlags.viaTnt=true とともに Dispatcher に流す。ここでは native な破壊のみ扱う。
 */
public final class BlockBreakListener implements Listener {

    private final EventDispatcher dispatcher;

    public BlockBreakListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        BlockData data = event.getBlock().getBlockData();
        boolean cropMature = false;
        if (data instanceof Ageable ageable) {
            cropMature = ageable.getAge() == ageable.getMaximumAge();
        }
        MatchContext ctx = MatchContext.builder()
                .block(event.getBlock().getType().getKey())
                .cropMature(cropMature)
                .viaTnt(false)
                .amount(1)
                .build();
        dispatcher.dispatch(event.getPlayer(), ActionType.BLOCK_BROKEN, ctx, SourceFlags.none());
    }
}
