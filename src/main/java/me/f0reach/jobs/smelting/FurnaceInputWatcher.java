package me.f0reach.jobs.smelting;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * かまどの投入を検知し、精錬元帳を入力スロットの実個数へ同期させる。
 *
 * <p>spec/03-action-detection.md 「item_smelted」および ADR-0024 を参照。
 *
 * <p>イベントは「直前に触ったのは誰か」のヒントとしてのみ使い、スロット位置は推測しない。
 * イベント時点ではまだアイテムが動いていないので、照合は 1 tick 後に行う。
 *
 * <p>この形にすると燃料スロットへの投入は入力スロットを動かさないので元帳が変わらず、
 * ドラッグ投入や入れ直しも同じ経路で吸収される。
 */
public final class FurnaceInputWatcher implements Listener {

    /** 照合待ち。toucher が null なら hopper 等の自動投入で、増分は報酬の対象外になる。 */
    private record Pending(Block block, UUID toucher) {}

    private final Plugin plugin;
    private final FurnaceLedgerStore store;
    private final Map<BlockRef, Pending> pending = new HashMap<>();

    public FurnaceInputWatcher(Plugin plugin, FurnaceLedgerStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Player の UI 操作。入力スロットに入ったかはここでは判定しない。 */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Block block = furnaceBlock(event.getInventory());
        if (block != null) schedule(block, event.getWhoClicked().getUniqueId());
    }

    /** ドラッグでの分配投入。{@link InventoryClickEvent} では拾えない。 */
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Block block = furnaceBlock(event.getInventory());
        if (block != null) schedule(block, event.getWhoClicked().getUniqueId());
    }

    /** hopper / dropper 由来の移動。所有者なしとして帰属させる。 */
    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        Block destination = furnaceBlock(event.getDestination());
        if (destination != null) schedule(destination, null);
        Block source = furnaceBlock(event.getSource());
        if (source != null) schedule(source, null);
    }

    /**
     * 1 tick 後の照合を予約する。同 tick に複数回触られたら最後の接触者を採る。
     * 差分は net で見るので、同 tick に人と hopper が混ざった分は最後の接触者へ寄る。
     */
    private void schedule(Block block, UUID toucher) {
        BlockRef ref = BlockRef.of(block);
        boolean alreadyScheduled = pending.containsKey(ref);
        pending.put(ref, new Pending(block, toucher));
        if (!alreadyScheduled) {
            plugin.getServer().getScheduler().runTask(plugin, () -> reconcile(ref));
        }
    }

    /** 入力スロットの実態を読み、元帳を合わせる。 */
    private void reconcile(BlockRef ref) {
        Pending target = pending.remove(ref);
        if (target == null) return;
        Block block = target.block();
        if (!(block.getState(false) instanceof Furnace furnace)) return;

        ItemStack input = furnace.getInventory().getSmelting();
        NamespacedKey itemKey = null;
        int count = 0;
        if (input != null && input.getType() != Material.AIR) {
            itemKey = input.getType().getKey();
            count = input.getAmount();
        }

        SmeltLedger ledger = store.load(block);
        ledger.sync(itemKey, count, target.toucher());
        store.save(block, ledger);
    }

    /** かまど系 inventory ならその Block を返す。 */
    private static Block furnaceBlock(Inventory inventory) {
        if (inventory == null) return null;
        return inventory.getHolder() instanceof Furnace furnace ? furnace.getBlock() : null;
    }

    /**
     * テストと診断から使う。照合待ちの件数。
     * 通常は次の tick で 0 に戻る。
     */
    public int pendingCount() {
        return pending.size();
    }
}
