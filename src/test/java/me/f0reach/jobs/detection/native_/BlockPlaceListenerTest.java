package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.antiautomation.AntiAutomationCoordinator;
import me.f0reach.jobs.antiautomation.AntiAutomationNotifier;
import me.f0reach.jobs.antiautomation.PlacementRecorder;
import me.f0reach.jobs.antiautomation.PlantedFlagWriter;
import me.f0reach.jobs.antiautomation.RecentlyPlacedReplaceCheck;
import me.f0reach.jobs.detection.EventDispatcher;
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
import me.f0reach.jobs.kvs.memory.InMemoryKVStore;
import me.f0reach.jobs.matcher.RewardMatcher;
import me.f0reach.jobs.pipeline.RewardPipeline;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.pipeline.stage.AntiAutomationStage;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.registry.TagResolver;
import me.f0reach.jobs.specialty.CooldownPolicy;
import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.testsupport.FixedFirstJoinProvider;
import me.f0reach.jobs.testsupport.InMemoryPlayerJobHistoryRepository;
import me.f0reach.jobs.testsupport.InMemoryPlayerJobRepository;
import me.f0reach.jobs.testsupport.InlineRewardDispatcher;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BlockPlaceListener が「dispatch してからマーカーを書く」順序を守っていることの回帰テスト。
 * 逆順だと recently_placed_replace が自分の書いたマーカーを読み、初回の設置まで 0 になる。
 */
class BlockPlaceListenerTest {

    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;

    private final List<List<String>> observedZeroReasons = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("main");
        plugin = MockBukkit.createMockPlugin("Jobs");
        observedZeroReasons.clear();
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private JobDefinition farmerJob() {
        MatchCriteria criteria = new MatchCriteria.BlockPlaced(
                new KeyMatcher.Single(NamespacedKey.minecraft("wheat")));
        RewardEntry entry = new RewardEntry(
                ActionType.BLOCK_PLACED, criteria, new RewardAmount.Fixed(1.0), null,
                new ActionKey("place:minecraft:wheat"));
        AntiAutomationConfig cfg = new AntiAutomationConfig(
                null, null, new AntiAutomationConfig.RecentlyPlacedBreak(60, true), null, null, null);
        return new JobDefinition(
                new JobId("farmer"), "Farmer", null, NamespacedKey.minecraft("wheat"),
                List.of(entry), VarietyPenaltyConfig.disabled(), cfg
        );
    }

    private BlockPlaceListener buildListener(Player player, InMemoryKVStore kv) {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(farmerJob()));
        SpecialtyService specialty = new SpecialtyService(
                plugin, new InMemoryPlayerJobRepository(), new InMemoryPlayerJobHistoryRepository(),
                registry, new CooldownPolicy(List.of()), FixedFirstJoinProvider.unknown()
        );
        specialty.loadPlayer(player.getUniqueId());
        specialty.select(player, new JobId("farmer"));

        AntiAutomationCoordinator coordinator = new AntiAutomationCoordinator(
                plugin, List.of(new RecentlyPlacedReplaceCheck(kv)));
        // notify は空 map なので i18n には触れない。
        Stage antiAutomation = new AntiAutomationStage(
                coordinator, new AntiAutomationNotifier(null, Map.of()));
        Stage recorder = ctx -> {
            observedZeroReasons.add(ctx.zeroReasons());
            return Stage.Result.CONTINUE;
        };
        RewardPipeline pipeline = new RewardPipeline(
                plugin, registry, new InlineRewardDispatcher(), List.of(antiAutomation, recorder));
        EventDispatcher dispatcher = new EventDispatcher(
                specialty, registry, new RewardMatcher(new TagResolver()), pipeline);

        return new BlockPlaceListener(
                dispatcher,
                new PlantedFlagWriter(new NamespacedKey(plugin, "planted_by_player")),
                new PlacementRecorder(kv),
                specialty,
                registry
        );
    }

    private void place(BlockPlaceListener listener, Player player, Block block) {
        block.setType(Material.WHEAT);
        Block against = world.getBlockAt(block.getX(), block.getY() - 1, block.getZ());
        against.setType(Material.FARMLAND);
        listener.onPlace(new BlockPlaceEvent(
                block, block.getState(), against, new ItemStack(Material.WHEAT_SEEDS),
                player, true, EquipmentSlot.HAND
        ));
    }

    @Test
    void firstPlacementIsRewardedAndReplantIsZeroed() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Player player = server.addPlayer();
        BlockPlaceListener listener = buildListener(player, kv);
        Block block = world.getBlockAt(5, 64, 5);

        place(listener, player, block);
        // 壊してから同じ位置に植え直す (block を消しても place: の記録は TTL まで残る)
        block.setType(Material.AIR);
        place(listener, player, block);

        assertEquals(2, observedZeroReasons.size());
        assertTrue(observedZeroReasons.get(0).isEmpty(), "初回の設置は 0 にならない");
        assertEquals(List.of(RecentlyPlacedReplaceCheck.REASON), observedZeroReasons.get(1));
    }

    @Test
    void placementAtAnotherPositionIsRewarded() {
        InMemoryKVStore kv = new InMemoryKVStore(1000);
        Player player = server.addPlayer();
        BlockPlaceListener listener = buildListener(player, kv);

        place(listener, player, world.getBlockAt(6, 64, 6));
        place(listener, player, world.getBlockAt(7, 64, 6));

        assertEquals(2, observedZeroReasons.size());
        assertFalse(observedZeroReasons.stream().anyMatch(r -> !r.isEmpty()),
                "位置が違えば 0 にならない");
    }
}
