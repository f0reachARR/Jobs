package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.ConsumeCategory;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * item_consumed: 食べる / 飲む アイテムの消費。category は Material から判定する。
 */
public final class ConsumeListener implements Listener {

    /** category=drink とみなす Material の集合。 */
    private static final Set<Material> DRINKS = Set.of(
            Material.POTION,
            Material.MILK_BUCKET,
            Material.HONEY_BOTTLE
    );

    private final EventDispatcher dispatcher;

    public ConsumeListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        Material material = stack.getType();
        ConsumeCategory category = DRINKS.contains(material) ? ConsumeCategory.DRINK
                : (material.isEdible() ? ConsumeCategory.FOOD : null);

        MatchContext ctx = MatchContext.builder()
                .item(material.getKey())
                .consumedCategory(category)
                .amount(1)
                .build();
        dispatcher.dispatch(event.getPlayer(), ActionType.ITEM_CONSUMED, ctx, SourceFlags.none());
    }
}
