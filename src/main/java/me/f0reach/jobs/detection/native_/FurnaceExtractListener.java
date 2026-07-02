package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;

/**
 * item_smelted: Furnace / BlastFurnace / Smoker いずれかから完成品を取り出したとき。
 * amount には getItemAmount を載せる (spec/04-reward-pipeline.md 「基礎報酬」)。
 */
public final class FurnaceExtractListener implements Listener {

    private final EventDispatcher dispatcher;

    public FurnaceExtractListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onExtract(FurnaceExtractEvent event) {
        MatchContext ctx = MatchContext.builder()
                .item(event.getItemType().getKey())
                .amount(Math.max(1, event.getItemAmount()))
                .build();
        dispatcher.dispatch(event.getPlayer(), ActionType.ITEM_SMELTED, ctx, SourceFlags.none());
    }
}
