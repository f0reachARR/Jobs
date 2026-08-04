package me.f0reach.jobs.smelting;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnaceLedgerStoreTest {

    private static final NamespacedKey RAW_IRON = NamespacedKey.minecraft("raw_iron");
    private static final UUID ALICE = UUID.randomUUID();

    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;
    private FurnaceLedgerStore store;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("main");
        plugin = MockBukkit.createMockPlugin("Jobs");
        store = new FurnaceLedgerStore(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block furnace(int x, Material type) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(type);
        return block;
    }

    @Test
    void savesAndLoadsLedger() {
        Block block = furnace(1, Material.FURNACE);
        SmeltLedger ledger = SmeltLedger.empty();
        ledger.sync(RAW_IRON, 8, ALICE);

        assertTrue(store.save(block, ledger));

        SmeltLedger loaded = store.load(block);
        assertEquals(RAW_IRON, loaded.itemKey());
        assertEquals(List.of(new SmeltLedger.Entry(ALICE, 8)), loaded.entries());
        assertFalse(loaded.isDirty());
    }

    @Test
    void loadsEmptyLedgerForUntrackedFurnace() {
        assertTrue(store.load(furnace(2, Material.BLAST_FURNACE)).isEmpty());
    }

    /** かまど以外は対象外。読み書きともに何もしない。 */
    @Test
    void ignoresNonFurnaceBlock() {
        Block block = furnace(3, Material.STONE);
        SmeltLedger ledger = SmeltLedger.empty();
        ledger.sync(RAW_IRON, 8, ALICE);

        assertFalse(store.save(block, ledger));
        assertTrue(store.load(block).isEmpty());
    }

    /** 変更が無ければ書かない。PDC 書き込みとチャンクの dirty 化を避ける。 */
    @Test
    void skipsSaveWhenNotDirty() {
        Block block = furnace(4, Material.SMOKER);
        SmeltLedger ledger = SmeltLedger.empty();
        ledger.sync(RAW_IRON, 8, ALICE);
        store.save(block, ledger);

        assertFalse(store.save(block, ledger));
    }

    @Test
    void removesKeyWhenLedgerBecomesEmpty() {
        Block block = furnace(5, Material.FURNACE);
        SmeltLedger ledger = SmeltLedger.empty();
        ledger.sync(RAW_IRON, 1, ALICE);
        store.save(block, ledger);

        ledger.consumeOne(RAW_IRON);
        assertTrue(store.save(block, ledger));
        assertTrue(store.load(block).isEmpty());

        // 既に消えている元帳は書き込みを起こさない。
        assertFalse(store.save(block, ledger));
    }
}
