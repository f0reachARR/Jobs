package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;

/**
 * item_brewed: BrewingStand の醸造完了。amount には出力 slot 数 (0〜3) を載せる。
 *
 * <p>BrewEvent は event 単位で発火するが、投入者 (operator) の情報は event に無いため
 * Phase 5 では BrewingStand の周囲にいるプレイヤーの中で最も近い 1 人を仮の owner にする。
 * Phase 7 で auto_fed_processing の OperatorTracker と組み合わせて厳密化する。
 */
public final class BrewListener implements Listener {

    private static final double OPERATOR_SEARCH_RADIUS = 8.0;

    private final EventDispatcher dispatcher;

    public BrewListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onBrew(BrewEvent event) {
        Player operator = nearestPlayer(event);
        if (operator == null) return;

        NamespacedKey itemKey = null;
        int outputCount = 0;
        for (ItemStack stack : event.getResults()) {
            if (stack == null || stack.getType().isAir()) continue;
            outputCount++;
            if (itemKey == null) itemKey = stack.getType().getKey();
        }
        if (outputCount == 0 || itemKey == null) return;

        MatchContext ctx = MatchContext.builder()
                .item(itemKey)
                .amount(outputCount)
                .build();
        dispatcher.dispatch(operator, ActionType.ITEM_BREWED, ctx, SourceFlags.none());
    }

    private Player nearestPlayer(BrewEvent event) {
        var loc = event.getBlock().getLocation().toCenterLocation();
        Player nearest = null;
        double nearestDistSq = OPERATOR_SEARCH_RADIUS * OPERATOR_SEARCH_RADIUS;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(loc.getWorld())) continue;
            double distSq = p.getLocation().distanceSquared(loc);
            if (distSq < nearestDistSq) {
                nearest = p;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }
}
