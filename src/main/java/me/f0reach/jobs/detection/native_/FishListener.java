package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * item_fished: CAUGHT_FISH または CAUGHT_ENTITY 時。treasure 判定は loot table を引く API が無いため、
 * treasure テーブルの定番アイテム集合を内蔵する。
 *
 * spec/03-action-detection.md 「crop_mature と treasure の判定」を参照。
 */
public final class FishListener implements Listener {

    /**
     * Vanilla の fishing/treasure loot テーブル相当のアイテム集合。
     * Vanilla 側で追加された場合はここに足す。YAML で上書きする余地は Phase 6 以降で検討する。
     */
    private static final Set<Material> TREASURE_ITEMS = Set.of(
            Material.NAME_TAG,
            Material.SADDLE,
            Material.BOW,
            Material.FISHING_ROD,
            Material.ENCHANTED_BOOK,
            Material.NAUTILUS_SHELL,
            Material.LILY_PAD
    );

    private final EventDispatcher dispatcher;

    public FishListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                && event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        var caught = event.getCaught();
        Material material = null;
        if (caught instanceof Item itemEntity) {
            ItemStack stack = itemEntity.getItemStack();
            material = stack.getType();
        }
        boolean treasure = material != null && TREASURE_ITEMS.contains(material);

        MatchContext ctx = MatchContext.builder()
                .item(material == null ? null : material.getKey())
                .treasure(treasure)
                .amount(1)
                .build();
        dispatcher.dispatch(event.getPlayer(), ActionType.ITEM_FISHED, ctx, SourceFlags.none());
    }
}
