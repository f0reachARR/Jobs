package me.f0reach.jobs.smelting;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 投入検知から元帳の同期まで。ADR-0024 を参照。
 *
 * <p>スロット位置を推測せず、入力スロットの実個数へ同期する設計の回帰テスト。
 * 燃料スロットへの投入で元帳が動かないこと（ADR-0017 の実装が守れていなかった点）を含む。
 */
class FurnaceInputWatcherTest {

    private static final NamespacedKey RAW_IRON = NamespacedKey.minecraft("raw_iron");

    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;
    private FurnaceLedgerStore store;
    private FurnaceInputWatcher watcher;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("main");
        plugin = MockBukkit.createMockPlugin("Jobs");
        store = new FurnaceLedgerStore(plugin);
        watcher = new FurnaceInputWatcher(plugin, store);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block furnace(int x) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.FURNACE);
        return block;
    }

    private FurnaceInventory inventoryOf(Block block) {
        return ((Furnace) block.getState(false)).getInventory();
    }

    private void click(Player player, Inventory inventory, int rawSlot) {
        InventoryView view = player.openInventory(inventory);
        watcher.onClick(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot,
                ClickType.LEFT, InventoryAction.PLACE_ALL));
    }

    /** イベント時点ではまだアイテムが動いていないので、照合は次の tick で走る。 */
    @Test
    void creditsPlayerInsertionAfterOneTick() {
        Block block = furnace(1);
        Player alice = server.addPlayer();
        inventoryOf(block).setSmelting(new ItemStack(Material.RAW_IRON, 8));

        click(alice, inventoryOf(block), 0);
        assertEquals(1, watcher.pendingCount(), "同 tick では照合しない");
        assertTrue(store.load(block).isEmpty());

        server.getScheduler().performTicks(1);

        SmeltLedger ledger = store.load(block);
        assertEquals(RAW_IRON, ledger.itemKey());
        assertEquals(List.of(new SmeltLedger.Entry(alice.getUniqueId(), 8)), ledger.entries());
        assertEquals(0, watcher.pendingCount());
    }

    /**
     * 燃料スロットへの投入では入力スロットが動かないので元帳が変わらない。
     * 石炭のホッパー供給と材料の手動投入が混在する構成を巻き込まないための要件。
     */
    @Test
    void fuelInsertionDoesNotTouchLedger() {
        Block block = furnace(2);
        Player alice = server.addPlayer();
        inventoryOf(block).setFuel(new ItemStack(Material.COAL, 64));

        click(alice, inventoryOf(block), 1);
        server.getScheduler().performTicks(1);

        assertTrue(store.load(block).isEmpty());
    }

    /** hopper 由来の投入は所有者なしとして積む。 */
    @Test
    void creditsHopperInsertionAsAutomation() {
        Block block = furnace(3);
        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);
        inventoryOf(block).setSmelting(new ItemStack(Material.RAW_IRON, 16));

        watcher.onMove(new InventoryMoveItemEvent(
                hopper, new ItemStack(Material.RAW_IRON, 16), inventoryOf(block), true));
        server.getScheduler().performTicks(1);

        assertEquals(List.of(new SmeltLedger.Entry(null, 16)), store.load(block).entries());
    }

    /** 人の投入とホッパーの追加が混ざっても、投入順に別エントリで積む。 */
    @Test
    void keepsInsertionOrderAcrossPlayerAndHopper() {
        Block block = furnace(4);
        Player alice = server.addPlayer();
        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);

        inventoryOf(block).setSmelting(new ItemStack(Material.RAW_IRON, 8));
        click(alice, inventoryOf(block), 0);
        server.getScheduler().performTicks(1);

        inventoryOf(block).setSmelting(new ItemStack(Material.RAW_IRON, 64));
        watcher.onMove(new InventoryMoveItemEvent(
                hopper, new ItemStack(Material.RAW_IRON, 56), inventoryOf(block), true));
        server.getScheduler().performTicks(1);

        assertEquals(
                List.of(new SmeltLedger.Entry(alice.getUniqueId(), 8),
                        new SmeltLedger.Entry(null, 56)),
                store.load(block).entries());
    }

    /** 入れたものを取り出したら、その分は元帳から消える。 */
    @Test
    void withdrawalShrinksLedger() {
        Block block = furnace(5);
        Player alice = server.addPlayer();

        inventoryOf(block).setSmelting(new ItemStack(Material.RAW_IRON, 20));
        click(alice, inventoryOf(block), 0);
        server.getScheduler().performTicks(1);

        inventoryOf(block).setSmelting(new ItemStack(Material.RAW_IRON, 5));
        click(alice, inventoryOf(block), 0);
        server.getScheduler().performTicks(1);

        assertEquals(List.of(new SmeltLedger.Entry(alice.getUniqueId(), 5)),
                store.load(block).entries());
    }

    /** 入力スロットを空にしたら元帳も消える。 */
    @Test
    void emptyingInputClearsLedger() {
        Block block = furnace(6);
        Player alice = server.addPlayer();

        inventoryOf(block).setSmelting(new ItemStack(Material.RAW_IRON, 4));
        click(alice, inventoryOf(block), 0);
        server.getScheduler().performTicks(1);

        inventoryOf(block).setSmelting(null);
        click(alice, inventoryOf(block), 0);
        server.getScheduler().performTicks(1);

        assertTrue(store.load(block).isEmpty());
    }

    /** かまど以外の inventory は無視する。 */
    @Test
    void ignoresNonFurnaceInventory() {
        Player alice = server.addPlayer();
        click(alice, server.createInventory(null, InventoryType.CHEST), 0);

        assertEquals(0, watcher.pendingCount());
    }

    /** 同 tick に複数回触られても照合は 1 回に畳む。 */
    @Test
    void coalescesMultipleTouchesInSameTick() {
        Block block = furnace(7);
        Player alice = server.addPlayer();
        inventoryOf(block).setSmelting(new ItemStack(Material.RAW_IRON, 12));

        click(alice, inventoryOf(block), 0);
        click(alice, inventoryOf(block), 0);
        click(alice, inventoryOf(block), 0);
        assertEquals(1, watcher.pendingCount());

        server.getScheduler().performTicks(1);

        assertEquals(List.of(new SmeltLedger.Entry(alice.getUniqueId(), 12)),
                store.load(block).entries());
    }
}
