package me.f0reach.jobs.smelting;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.native_.FurnaceSmeltListener;
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
import me.f0reach.jobs.matcher.RewardMatcher;
import me.f0reach.jobs.pipeline.RewardPipeline;
import me.f0reach.jobs.pipeline.Stage;
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
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.FurnaceRecipe;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 精錬完了から報酬 dispatch までの流れ。ADR-0024 を参照。
 *
 * <p>報酬が「取り出した人」ではなく元帳の投入者に入ること、
 * 自動投入ぶんとオフラインぶんが払われないこと、amount が集約されることを見る。
 */
class SmeltRewardFlowTest {

    private static final NamespacedKey RAW_IRON = NamespacedKey.minecraft("raw_iron");

    /** dispatch された (プレイヤー, amount) の記録。 */
    private record Dispatched(UUID player, int amount) {}

    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;
    private final List<Dispatched> dispatched = new ArrayList<>();

    private FurnaceLedgerStore store;
    private SmeltRewardCollector collector;
    private FurnaceSmeltListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("main");
        plugin = MockBukkit.createMockPlugin("Jobs");
        dispatched.clear();
        store = new FurnaceLedgerStore(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private JobDefinition smelterJob() {
        MatchCriteria criteria = new MatchCriteria.ItemSmelted(
                new KeyMatcher.Single(NamespacedKey.minecraft("iron_ingot")));
        RewardEntry entry = new RewardEntry(
                ActionType.ITEM_SMELTED, criteria, new RewardAmount.Fixed(1.0), null,
                new ActionKey("smelt:minecraft:iron_ingot"));
        return new JobDefinition(
                new JobId("smelter"), "Smelter", null, NamespacedKey.minecraft("furnace"),
                List.of(entry), VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());
    }

    /** 専業を選んだプレイヤーを 1 人用意し、listener と collector を組む。 */
    private Player setUpSmelter() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(smelterJob()));
        SpecialtyService specialty = new SpecialtyService(
                plugin, new InMemoryPlayerJobRepository(), new InMemoryPlayerJobHistoryRepository(),
                registry, new CooldownPolicy(List.of()), FixedFirstJoinProvider.unknown());

        Stage recorder = ctx -> {
            dispatched.add(new Dispatched(ctx.playerUuid(), ctx.amount()));
            return Stage.Result.CONTINUE;
        };
        RewardPipeline pipeline = new RewardPipeline(
                plugin, registry, new InlineRewardDispatcher(), List.of(recorder));
        EventDispatcher dispatcher = new EventDispatcher(
                specialty, registry, new RewardMatcher(new TagResolver()), pipeline);

        // flush は明示的に呼ぶ。tick 待ちを挟まずに検証する。
        collector = new SmeltRewardCollector(plugin, dispatcher, 20L, 4096);
        listener = new FurnaceSmeltListener(store, collector);

        Player player = server.addPlayer();
        specialty.loadPlayer(player.getUniqueId());
        specialty.select(player, new JobId("smelter"));
        return player;
    }

    private Block furnace(int x) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.FURNACE);
        return block;
    }

    private void writeLedger(Block block, SmeltLedger.Entry... entries) {
        store.save(block, SmeltLedger.of(RAW_IRON, List.of(entries)));
    }

    private void smelt(Block block) {
        smelt(block, new ItemStack(Material.IRON_INGOT));
    }

    private void smelt(Block block, ItemStack result) {
        ItemStack source = new ItemStack(Material.RAW_IRON);
        FurnaceRecipe recipe = new FurnaceRecipe(
                NamespacedKey.minecraft("iron_ingot_from_raw_iron"),
                result, Material.RAW_IRON, 0.7f, 200);
        listener.onSmelt(new FurnaceSmeltEvent(block, source, result, recipe));
    }

    /** 投入者に払う。取り出しは介在しない。 */
    @Test
    void paysInserterOnSmeltCompletion() {
        Player alice = setUpSmelter();
        Block block = furnace(1);
        writeLedger(block, new SmeltLedger.Entry(alice.getUniqueId(), 2));

        smelt(block);
        collector.flush();

        assertEquals(List.of(new Dispatched(alice.getUniqueId(), 1)), dispatched);
        assertEquals(1, store.load(block).total(), "引き当てたぶんだけ元帳が減る");
    }

    /** 連続する精錬は 1 件に畳んで amount へ寄せる。 */
    @Test
    void aggregatesConsecutiveSmeltsIntoSingleDispatch() {
        Player alice = setUpSmelter();
        Block block = furnace(2);
        writeLedger(block, new SmeltLedger.Entry(alice.getUniqueId(), 3));

        smelt(block);
        smelt(block);
        smelt(block);
        collector.flush();

        assertEquals(List.of(new Dispatched(alice.getUniqueId(), 3)), dispatched);
        assertTrue(store.load(block).isEmpty());
    }

    /** かまどが違えば別の 1 件にする。従来の「取り出し 1 回で 1 件」に近い粒度を保つ。 */
    @Test
    void keepsFurnacesSeparate() {
        Player alice = setUpSmelter();
        Block first = furnace(3);
        Block second = furnace(4);
        writeLedger(first, new SmeltLedger.Entry(alice.getUniqueId(), 1));
        writeLedger(second, new SmeltLedger.Entry(alice.getUniqueId(), 1));

        smelt(first);
        smelt(second);
        collector.flush();

        assertEquals(2, dispatched.size());
    }

    /** hopper 投入ぶん (所有者なし) は消費だけ進み、誰にも払わない。 */
    @Test
    void paysNobodyForAutomationEntry() {
        setUpSmelter();
        Block block = furnace(5);
        writeLedger(block, new SmeltLedger.Entry(null, 1));

        smelt(block);
        collector.flush();

        assertTrue(dispatched.isEmpty());
        assertTrue(store.load(block).isEmpty(), "払わなくても元帳は消費する");
    }

    /** 元帳が無いかまど (プラグイン導入前の投入など) も払わない。 */
    @Test
    void paysNobodyWithoutLedger() {
        setUpSmelter();
        Block block = furnace(6);

        smelt(block);
        collector.flush();

        assertTrue(dispatched.isEmpty());
    }

    /** オフラインの投入者は消費のみ。 */
    @Test
    void paysNobodyWhenInserterIsOffline() {
        setUpSmelter();
        Block block = furnace(7);
        writeLedger(block, new SmeltLedger.Entry(UUID.randomUUID(), 1));

        smelt(block);
        collector.flush();

        assertTrue(dispatched.isEmpty());
        assertTrue(store.load(block).isEmpty());
    }

    /** 元帳の材料と違うものが焼けたら元帳を破棄し、他人の帰属で払わない。 */
    @Test
    void discardsLedgerOnItemMismatch() {
        Player alice = setUpSmelter();
        Block block = furnace(8);
        store.save(block, SmeltLedger.of(
                NamespacedKey.minecraft("raw_gold"),
                List.of(new SmeltLedger.Entry(alice.getUniqueId(), 8))));

        smelt(block);
        collector.flush();

        assertTrue(dispatched.isEmpty());
        assertTrue(store.load(block).isEmpty());
    }

    /** 1 回の精錬で 2 個出るレシピは個数どおりに数える。 */
    @Test
    void countsMultiOutputResult() {
        Player alice = setUpSmelter();
        Block block = furnace(9);
        writeLedger(block, new SmeltLedger.Entry(alice.getUniqueId(), 1));

        smelt(block, new ItemStack(Material.IRON_INGOT, 2));
        collector.flush();

        assertEquals(List.of(new Dispatched(alice.getUniqueId(), 2)), dispatched);
    }
}
