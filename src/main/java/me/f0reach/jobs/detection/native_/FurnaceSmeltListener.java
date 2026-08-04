package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.smelting.FurnaceLedgerStore;
import me.f0reach.jobs.smelting.SmeltLedger;
import me.f0reach.jobs.smelting.SmeltRewardCollector;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * item_smelted: Furnace / BlastFurnace / Smoker で 1 個焼き上がったとき。
 *
 * <p>報酬は取り出した人ではなく材料を投入した人に払う（ADR-0024）。
 * 投入者は {@link FurnaceLedgerStore} の精錬元帳から先頭 1 個ぶんを引き当てて決める。
 *
 * <p>所有者が null（hopper 等の自動投入、元帳が空、材料の種別不一致）なら消費だけを行い、
 * 報酬は発生させない。かつての {@code auto_fed_processing} のかまど分はここに吸収されている。
 *
 * <p>他プラグインが精錬をキャンセルしたぶんを引き当てないよう MONITOR かつ ignoreCancelled で受ける。
 */
public final class FurnaceSmeltListener implements Listener {

    private final FurnaceLedgerStore ledgerStore;
    private final SmeltRewardCollector collector;

    public FurnaceSmeltListener(FurnaceLedgerStore ledgerStore, SmeltRewardCollector collector) {
        this.ledgerStore = ledgerStore;
        this.collector = collector;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        Block block = event.getBlock();
        SmeltLedger ledger = ledgerStore.load(block);
        UUID owner = ledger.consumeOne(itemKeyOf(event.getSource()));
        ledgerStore.save(block, ledger);
        if (owner == null) return;

        ItemStack result = event.getResult();
        NamespacedKey resultKey = itemKeyOf(result);
        if (resultKey == null) return;
        // 1 回の精錬で 2 個以上出るレシピ（データパック）も個数どおりに数える。
        collector.credit(owner, block, resultKey, Math.max(1, result.getAmount()));
    }

    private static NamespacedKey itemKeyOf(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;
        return stack.getType().getKey();
    }
}
