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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentlyPlacedReplaceCheckTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("main");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private static AntiAutomationConfig enabled() {
        return new AntiAutomationConfig(
                null, null, new AntiAutomationConfig.RecentlyPlacedBreak(60, true), null, null, null
        );
    }

    private PipelineContext ctx(AntiAutomationConfig cfg, Block block) {
        return ctx(cfg, block, ActionType.BLOCK_PLACED);
    }

    private PipelineContext ctx(AntiAutomationConfig cfg, Block block, ActionType actionType) {
        Player player = server.addPlayer();
        MatchCriteria criteria = actionType == ActionType.BLOCK_PLACED
                ? new MatchCriteria.BlockPlaced(new KeyMatcher.Single(NamespacedKey.minecraft("wheat")))
                : new MatchCriteria.BlockBroken(
                        new KeyMatcher.Single(NamespacedKey.minecraft("wheat")), false, false);
        RewardEntry entry = new RewardEntry(
                actionType, criteria, new RewardAmount.Fixed(1.0), null,
                new ActionKey("place:minecraft:wheat"));
        JobDefinition job = new JobDefinition(
                new JobId("farmer"), "Farmer", null, NamespacedKey.minecraft("wheat"),
                List.of(entry), VarietyPenaltyConfig.disabled(), cfg
        );
        DetectionSubject subject = DetectionSubject.builder().block(block).build();
        DetectedAction action = new DetectedAction(
                player, job.id(), entry, entry.derivedKey(), 1, SourceFlags.none(), subject
        );
        return new PipelineContext(action, job, Instant.now());
    }

    @Test
    void placingWhereNothingWasPlacedReturnsNull() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(10, 64, 10);
        block.setType(Material.WHEAT);

        assertNull(new RecentlyPlacedReplaceCheck(kv).evaluate(ctx(enabled(), block)));
    }

    @Test
    void replacingRecentlyPlacedSpotReturnsReason() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(20, 64, 20);
        block.setType(Material.STONE);
        kv.put(KvsKeys.place(world.getUID(), 20, 64, 20), new byte[]{1}, Duration.ofSeconds(60));

        assertEquals(RecentlyPlacedReplaceCheck.REASON,
                new RecentlyPlacedReplaceCheck(kv).evaluate(ctx(enabled(), block)));
    }

    /** 破壊側と違い、作物 (Ageable) も設置側では判定対象にする (ADR-0023)。 */
    @Test
    void replantingCropReturnsReason() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(30, 64, 30);
        block.setType(Material.WHEAT);
        kv.put(KvsKeys.place(world.getUID(), 30, 64, 30), new byte[]{1}, Duration.ofSeconds(60));

        assertEquals(RecentlyPlacedReplaceCheck.REASON,
                new RecentlyPlacedReplaceCheck(kv).evaluate(ctx(enabled(), block)));
    }

    @Test
    void appliesOnlyToBlockPlaced() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(40, 64, 40);
        block.setType(Material.WHEAT);
        RecentlyPlacedReplaceCheck check = new RecentlyPlacedReplaceCheck(kv);

        assertTrue(check.appliesTo(ctx(enabled(), block), ActionType.BLOCK_PLACED));
        assertFalse(check.appliesTo(
                ctx(enabled(), block, ActionType.BLOCK_BROKEN), ActionType.BLOCK_BROKEN));
    }

    @Test
    void disabledOrUnsetConfigDoesNotApply() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Block block = world.getBlockAt(50, 64, 50);
        block.setType(Material.WHEAT);
        RecentlyPlacedReplaceCheck check = new RecentlyPlacedReplaceCheck(kv);

        AntiAutomationConfig off = new AntiAutomationConfig(
                null, null, AntiAutomationConfig.RecentlyPlacedBreak.disabled(), null, null, null);
        assertFalse(check.appliesTo(ctx(off, block), ActionType.BLOCK_PLACED));

        assertFalse(check.appliesTo(
                ctx(AntiAutomationConfig.empty(), block), ActionType.BLOCK_PLACED));
        assertFalse(check.appliesTo(ctx(null, block), ActionType.BLOCK_PLACED));
    }
}
