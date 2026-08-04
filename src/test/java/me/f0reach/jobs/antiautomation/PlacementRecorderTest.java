package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.kvs.memory.InMemoryKVStore;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementRecorderTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("main");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void recordsNonAgeableBlock() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(1, 64, 1);
        block.setType(Material.STONE);

        new PlacementRecorder(kv).recordPlacement(block, 60);

        assertTrue(kv.get(KvsKeys.place(world.getUID(), 1, 64, 1)).isPresent());
    }

    /** 作物も記録する。記録しないと再設置ループ (ADR-0023) を検出できない。 */
    @Test
    void recordsAgeableBlock() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(2, 64, 2);
        block.setType(Material.WHEAT);

        new PlacementRecorder(kv).recordPlacement(block, 60);

        assertTrue(kv.get(KvsKeys.place(world.getUID(), 2, 64, 2)).isPresent());
    }
}
