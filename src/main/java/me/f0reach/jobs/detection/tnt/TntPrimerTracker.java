package me.f0reach.jobs.detection.tnt;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * TNT の起爆者を追跡し、TNT 由来の block_broken を dispatch する。
 *
 * spec/03-action-detection.md 「TNT 起爆者の追跡」を参照。
 * TNTPrimed の PDC に起爆者 UUID を書き、EntityExplodeEvent で対象 block ごとに
 * 合成 dispatch を発火する。
 */
public final class TntPrimerTracker implements Listener {

    private final Plugin plugin;
    private final EventDispatcher dispatcher;
    private final NamespacedKey primerKey;

    public TntPrimerTracker(Plugin plugin, EventDispatcher dispatcher) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.primerKey = new NamespacedKey(plugin, "tnt_primer");
    }

    /** flint & steel で TNT に着火した瞬間を捕まえる。 */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.TNT) return;
        var itemInHand = event.getPlayer().getInventory().getItemInMainHand();
        if (itemInHand.getType() != Material.FLINT_AND_STEEL
                && itemInHand.getType() != Material.FIRE_CHARGE) {
            return;
        }
        // TNT が primed になるのは Bukkit の同 tick 内。次 tick で primed を探して PDC を書く。
        var loc = block.getLocation();
        var world = block.getWorld();
        UUID primer = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (var entity : world.getNearbyEntities(loc.toCenterLocation(), 1.5, 1.5, 1.5)) {
                if (entity.getType() != EntityType.TNT) continue;
                entity.getPersistentDataContainer().set(primerKey, PersistentDataType.STRING, primer.toString());
            }
        });
    }

    /** TNT ブロックが BlockIgniteEvent で primed になったときも捕まえる。 */
    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getBlock().getType() != Material.TNT) return;
        Player igniter = event.getPlayer();
        if (igniter == null) return;
        UUID primer = igniter.getUniqueId();
        var loc = event.getBlock().getLocation();
        var world = event.getBlock().getWorld();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (var entity : world.getNearbyEntities(loc.toCenterLocation(), 1.5, 1.5, 1.5)) {
                if (entity.getType() != EntityType.TNT) continue;
                entity.getPersistentDataContainer().set(primerKey, PersistentDataType.STRING, primer.toString());
            }
        });
    }

    /**
     * TNT が爆発したときに、対象ブロックごとに合成 dispatch を発火する。
     * BlockBreakListener のパスとは分けて、via_tnt=true を立てる。
     */
    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        String uuidStr = tnt.getPersistentDataContainer().get(primerKey, PersistentDataType.STRING);
        if (uuidStr == null) return;
        UUID primerUuid;
        try {
            primerUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        Player primer = Bukkit.getPlayer(primerUuid);
        if (primer == null || !primer.isOnline()) return;

        SourceFlags flags = new SourceFlags(true, primerUuid, false, false, false, false, false);
        for (var block : event.blockList()) {
            MatchContext ctx = MatchContext.builder()
                    .block(block.getType().getKey())
                    .viaTnt(true)
                    .amount(1)
                    .build();
            dispatcher.dispatch(primer, ActionType.BLOCK_BROKEN, ctx, flags);
        }
    }
}
