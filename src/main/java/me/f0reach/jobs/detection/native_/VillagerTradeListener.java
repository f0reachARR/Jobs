package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * villager_traded: Merchant の result slot への click で取引成立時。
 * amount には取引個数を載せる。
 */
public final class VillagerTradeListener implements Listener {

    private final EventDispatcher dispatcher;

    public VillagerTradeListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT) return;
        // Merchant の result slot は 2
        if (event.getRawSlot() != 2) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MatchContext ctx = MatchContext.builder()
                .item(result.getType().getKey())
                .amount(Math.max(1, result.getAmount()))
                .build();
        dispatcher.dispatch(player, ActionType.VILLAGER_TRADED, ctx, SourceFlags.none());
    }
}
