package me.f0reach.jobs.smelting;

import me.f0reach.jobs.config.PluginConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * smelting セクションを {@code /jobs reload} で反映できること。
 *
 * <p>投入者はオフラインの UUID にしておく。flush はオフラインぶんを捨てるので
 * dispatcher へは届かず、まとめ待ち件数の変化だけで挙動を見られる（ADR-0024）。
 */
class SmeltRewardCollectorTest {

    private static final NamespacedKey IRON_INGOT = NamespacedKey.minecraft("iron_ingot");

    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;
    private AtomicReference<PluginConfig.SmeltingConfig> config;
    private SmeltRewardCollector collector;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("main");
        plugin = MockBukkit.createMockPlugin("Jobs");
        config = new AtomicReference<>(new PluginConfig.SmeltingConfig(20L, 4096));
        collector = new SmeltRewardCollector(plugin, null, config::get);
        collector.start();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void flushesOnTheConfiguredPeriod() {
        credit(0);

        server.getScheduler().performTicks(19);
        assertEquals(1, collector.pendingCount(), "周期前は溜めたまま");

        server.getScheduler().performTicks(1);
        assertEquals(0, collector.pendingCount());
    }

    @Test
    void reschedulesWhenFlushTicksChanges() {
        credit(1);
        config.set(new PluginConfig.SmeltingConfig(5L, 4096));

        collector.applyConfig();
        assertEquals(0, collector.pendingCount(), "組み直す前にまとめ待ちを出す");

        credit(2);
        server.getScheduler().performTicks(5);
        assertEquals(0, collector.pendingCount(), "新しい周期で flush される");
    }

    @Test
    void keepsTheTimerWhenFlushTicksIsUnchanged() {
        credit(3);
        config.set(new PluginConfig.SmeltingConfig(20L, 64));

        collector.applyConfig();
        assertEquals(1, collector.pendingCount(), "周期が同じなら flush も組み直しもしない");

        server.getScheduler().performTicks(20);
        assertEquals(0, collector.pendingCount());
    }

    @Test
    void readsMaxPendingOnEveryCredit() {
        config.set(new PluginConfig.SmeltingConfig(20L, 1));

        credit(4);

        assertEquals(0, collector.pendingCount(), "上限 1 なら積んだ時点で flush する");
    }

    private void credit(int x) {
        Block block = world.getBlockAt(x, 64, 0);
        collector.credit(UUID.randomUUID(), block, IRON_INGOT, 1);
    }
}
