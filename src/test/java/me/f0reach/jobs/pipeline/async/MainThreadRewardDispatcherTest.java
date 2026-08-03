package me.f0reach.jobs.pipeline.async;

import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code reward.async.enabled: false} の経路。全段階が呼び出しスレッド（main thread）で
 * 同期実行されることを確認する。
 */
class MainThreadRewardDispatcherTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void rewardTaskRunsInlineOnThePrimaryThread() {
        MainThreadRewardDispatcher dispatcher = new MainThreadRewardDispatcher(new AsyncExecutor(plugin));
        List<String> log = new ArrayList<>();

        assertTrue(server.isPrimaryThread(), "テストは main thread 相当で走る");
        boolean accepted = dispatcher.dispatchReward(() -> log.add("reward"));

        // 投入した時点で既に実行が終わっている（scheduler を跨がない）。
        assertTrue(accepted);
        assertEquals(List.of("reward"), log);
    }

    @Test
    void controlTaskRunsInlineToo() {
        MainThreadRewardDispatcher dispatcher = new MainThreadRewardDispatcher(new AsyncExecutor(plugin));
        List<String> log = new ArrayList<>();

        dispatcher.dispatchControl(() -> log.add("control"));

        assertEquals(List.of("control"), log);
    }

    @Test
    void orderBetweenRewardAndControlIsPreserved() {
        MainThreadRewardDispatcher dispatcher = new MainThreadRewardDispatcher(new AsyncExecutor(plugin));
        List<String> log = new ArrayList<>();

        dispatcher.dispatchReward(() -> log.add("r1"));
        dispatcher.dispatchControl(() -> log.add("c1"));
        dispatcher.dispatchReward(() -> log.add("r2"));

        assertEquals(List.of("r1", "c1", "r2"), log);
    }
}
