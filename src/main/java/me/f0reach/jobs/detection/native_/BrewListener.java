package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.antiautomation.ContainerKind;
import me.f0reach.jobs.detection.DetectionSubject;
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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * item_brewed: BrewingStand の醸造完了。
 *
 * <p>出力 slot ごとに 1 event ずつ dispatch する (amount=1)。
 * 各 slot の {@link PotionMeta#getBasePotionType()} を potionType として ctx に載せ、
 * `potion:` 条件で filter できるようにする (3 slot が別種類のとき slot ごとに評価される)。
 *
 * <p>BrewEvent には投入者 (operator) 情報が無いため、Phase 5 と同じく BrewingStand
 * 周囲の最近接プレイヤーを仮の owner にする。auto_fed_processing の operator 検査は
 * {@link me.f0reach.jobs.antiautomation.OperatorTracker} が KVS に書き込んだ値で行う
 * ため、per-slot dispatch でも同じ operator を read するだけで消費はしない。
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

        DetectionSubject subject = DetectionSubject.builder()
                .containerBlock(event.getBlock())
                .containerKind(ContainerKind.BREWING_STAND)
                .build();

        for (ItemStack stack : event.getResults()) {
            if (stack == null || stack.getType().isAir()) continue;
            NamespacedKey itemKey = stack.getType().getKey();
            NamespacedKey potionKey = basePotionKey(stack);

            MatchContext ctx = MatchContext.builder()
                    .item(itemKey)
                    .amount(1)
                    .potionType(potionKey)
                    .build();
            dispatcher.dispatch(operator, ActionType.ITEM_BREWED, ctx, SourceFlags.none(), subject);
        }
    }

    private static NamespacedKey basePotionKey(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof PotionMeta meta)) return null;
        if (!meta.hasBasePotionType()) return null;
        PotionType type = meta.getBasePotionType();
        return type == null ? null : type.getKey();
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
