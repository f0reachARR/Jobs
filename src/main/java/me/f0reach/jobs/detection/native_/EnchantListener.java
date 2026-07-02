package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * item_enchanted: エンチャントテーブルでの付与操作。
 */
public final class EnchantListener implements Listener {

    private final EventDispatcher dispatcher;

    public EnchantListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Map<NamespacedKey, Integer> enchantments = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> e : event.getEnchantsToAdd().entrySet()) {
            enchantments.put(e.getKey().getKey(), e.getValue());
        }
        MatchContext ctx = MatchContext.builder()
                .item(event.getItem().getType().getKey())
                .enchantments(enchantments)
                .amount(1)
                .build();
        dispatcher.dispatch(event.getEnchanter(), ActionType.ITEM_ENCHANTED, ctx, SourceFlags.none());
    }
}
