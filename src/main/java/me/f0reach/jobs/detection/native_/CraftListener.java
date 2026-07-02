package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * item_crafted: プレイヤーがクラフト結果を取り出したとき。
 * シフトクリック時の複数取り出し個数を amount に載せる。
 */
public final class CraftListener implements Listener {

    private final EventDispatcher dispatcher;

    public CraftListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getRecipe().getResult();
        if (result == null) return;
        int amount = calculateAmount(event, result);
        MatchContext ctx = MatchContext.builder()
                .item(result.getType().getKey())
                .amount(amount)
                .build();
        dispatcher.dispatch(player, ActionType.ITEM_CRAFTED, ctx, SourceFlags.none());
    }

    /**
     * シフトクリック時は「レシピ 1 回分の出力 × 実行可能回数」を返す。
     * 実行可能回数はクラフトグリッド内の各 slot の最小スタック数で決まる。
     * それ以外の click 種別は 1 レシピ分。
     */
    private int calculateAmount(CraftItemEvent event, ItemStack result) {
        int perRecipe = result.getAmount();
        if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) {
            return perRecipe;
        }
        if (!(event.getInventory() instanceof CraftingInventory inv)) return perRecipe;
        int recipes = Integer.MAX_VALUE;
        boolean any = false;
        for (ItemStack ingredient : inv.getMatrix()) {
            if (ingredient == null) continue;
            any = true;
            recipes = Math.min(recipes, ingredient.getAmount());
        }
        if (!any || recipes == Integer.MAX_VALUE) return perRecipe;
        return perRecipe * recipes;
    }
}
