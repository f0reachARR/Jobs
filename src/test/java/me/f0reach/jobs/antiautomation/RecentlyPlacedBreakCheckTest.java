package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.detection.DetectedAction;
import me.f0reach.jobs.detection.DetectionSubject;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionKey;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RewardAmount;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.kvs.memory.InMemoryKVStore;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecentlyPlacedBreakCheckTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("main");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private PipelineContext ctx(AntiAutomationConfig cfg, Block block) {
        Player player = server.addPlayer();
        MatchCriteria criteria = new MatchCriteria.BlockBroken(
                new KeyMatcher.Single(NamespacedKey.minecraft("stone")), false, false);
        RewardEntry entry = new RewardEntry(
                ActionType.BLOCK_BROKEN, criteria, new RewardAmount.Fixed(1.0), null,
                new ActionKey("break:minecraft:stone"));
        JobDefinition job = new JobDefinition(
                new JobId("miner"), "Miner", NamespacedKey.minecraft("iron_pickaxe"),
                List.of(entry), VarietyPenaltyConfig.disabled(), cfg
        );
        DetectionSubject subject = DetectionSubject.builder().block(block).build();
        DetectedAction action = new DetectedAction(
                player, job.id(), entry, entry.derivedKey(), 1, SourceFlags.none(), subject
        );
        return new PipelineContext(action, job, Instant.now());
    }

    @Test
    void breakOfRecentlyPlacedBlockReturnsReason() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(10, 64, 10);
        block.setType(Material.STONE);
        kv.put(KvsKeys.place(world.getUID(), 10, 64, 10), new byte[]{1}, Duration.ofSeconds(60));

        AntiAutomationConfig cfg = new AntiAutomationConfig(
                null, null, new AntiAutomationConfig.RecentlyPlacedBreak(60), null, null, null
        );
        String reason = new RecentlyPlacedBreakCheck(kv).evaluate(ctx(cfg, block));
        assertEquals(RecentlyPlacedBreakCheck.REASON, reason);
    }

    @Test
    void breakOfNaturalBlockReturnsNull() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(20, 64, 20);
        block.setType(Material.STONE);
        AntiAutomationConfig cfg = new AntiAutomationConfig(
                null, null, new AntiAutomationConfig.RecentlyPlacedBreak(60), null, null, null
        );
        assertNull(new RecentlyPlacedBreakCheck(kv).evaluate(ctx(cfg, block)));
    }

    @Test
    void ageableBlockIsSkipped() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(30, 64, 30);
        block.setType(Material.WHEAT);
        // 「置いた」情報が KVS にあっても、Ageable ならこの check は通過。
        kv.put(KvsKeys.place(world.getUID(), 30, 64, 30), new byte[]{1}, Duration.ofSeconds(60));

        AntiAutomationConfig cfg = new AntiAutomationConfig(
                null, null, new AntiAutomationConfig.RecentlyPlacedBreak(60), null, null, null
        );
        assertNull(new RecentlyPlacedBreakCheck(kv).evaluate(ctx(cfg, block)));
    }
}
