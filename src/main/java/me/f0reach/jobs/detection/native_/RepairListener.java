package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.RepairSource;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;

/**
 * item_repaired: Anvil の result slot 取り出しと Mending エンチャントによる修復。
 * source フィールド (anvil / mending) を context に載せる。
 */
public final class RepairListener implements Listener {

    private final EventDispatcher dispatcher;

    public RepairListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        // Anvil の result slot は slot 2
        if (event.getRawSlot() != 2) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MatchContext ctx = MatchContext.builder()
                .item(result.getType().getKey())
                .repairSource(RepairSource.ANVIL)
                .amount(1)
                .build();
        dispatcher.dispatch(player, ActionType.ITEM_REPAIRED, ctx, SourceFlags.none());
    }

    @EventHandler
    public void onMend(PlayerItemMendEvent event) {
        ItemStack item = event.getItem();
        MatchContext ctx = MatchContext.builder()
                .item(item.getType().getKey())
                .repairSource(RepairSource.MENDING)
                .amount(1)
                .build();
        dispatcher.dispatch(event.getPlayer(), ActionType.ITEM_REPAIRED, ctx, SourceFlags.none());
    }
}
