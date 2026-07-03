package me.f0reach.jobs.detection;

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
import me.f0reach.jobs.matcher.MatchContext;
import me.f0reach.jobs.matcher.RewardMatcher;
import me.f0reach.jobs.persistence.PlayerJobHistoryRepository;
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.Actor;
import me.f0reach.jobs.persistence.dto.PlayerJobHistoryRow;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import me.f0reach.jobs.pipeline.RewardPipeline;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.registry.TagResolver;
import me.f0reach.jobs.specialty.CooldownPolicy;
import me.f0reach.jobs.specialty.SpecialtyService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventDispatcherTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    /** in-memory PlayerJobRepository。SpecialtyService の unit test 版と同じ簡易実装。 */
    private static final class InMemoryRepo implements PlayerJobRepository {
        final Map<UUID, PlayerJobRow> rows = new HashMap<>();
        @Override public Optional<PlayerJobRow> find(UUID player) { return Optional.ofNullable(rows.get(player)); }
        @Override public void upsert(UUID player, String jobId, Instant cooldownBaseAt) {
            rows.put(player, new PlayerJobRow(player, jobId, cooldownBaseAt));
        }
        @Override public void resetCooldownBase(UUID player) {
            PlayerJobRow existing = rows.get(player);
            if (existing != null) rows.put(player, new PlayerJobRow(player, existing.jobId(), Instant.EPOCH));
        }
        @Override public void delete(UUID player) { rows.remove(player); }
        @Override public Map<String, Long> countByJob() {
            Map<String, Long> counts = new HashMap<>();
            for (PlayerJobRow row : rows.values()) counts.merge(row.jobId(), 1L, Long::sum);
            return counts;
        }
    }

    private static final class InMemoryHistory implements PlayerJobHistoryRepository {
        final List<PlayerJobHistoryRow> rows = new ArrayList<>();
        long nextId = 1;
        @Override public void append(UUID player, String jobId, String previousJobId,
                                     Instant changedAt, Actor actor, UUID actorUuid) {
            rows.add(new PlayerJobHistoryRow(nextId++, player, jobId, previousJobId, changedAt, actor, actorUuid));
        }
        @Override public List<PlayerJobHistoryRow> recent(UUID player, int limit) {
            return rows.stream().filter(r -> r.playerUuid().equals(player))
                    .sorted(Comparator.comparing(PlayerJobHistoryRow::changedAt).reversed())
                    .limit(limit).toList();
        }
        @Override public Optional<Instant> firstSelectedAt(UUID player) {
            return rows.stream().filter(r -> r.playerUuid().equals(player))
                    .map(PlayerJobHistoryRow::changedAt).min(Comparator.naturalOrder());
        }
    }

    private JobDefinition makeCombatJob() {
        MatchCriteria criteria = new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(ActionType.ENTITY_KILLED, criteria,
                new RewardAmount.Fixed(5), null, new ActionKey("kill:minecraft:zombie"));
        return new JobDefinition(
                new JobId("combat"), "Combat", null, NamespacedKey.minecraft("iron_sword"),
                List.of(entry), VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty()
        );
    }

    private EventDispatcher build(JobRegistry registry, SpecialtyService specialty, List<Stage> stages) {
        RewardMatcher matcher = new RewardMatcher(new TagResolver());
        RewardPipeline pipeline = new RewardPipeline(plugin, registry, stages);
        return new EventDispatcher(specialty, registry, matcher, pipeline);
    }

    @Test
    void noSpecialtyDoesNotInvokePipeline() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(makeCombatJob()));
        InMemoryRepo repo = new InMemoryRepo();
        InMemoryHistory history = new InMemoryHistory();
        SpecialtyService specialty = new SpecialtyService(
                plugin, repo, history, registry,
                new CooldownPolicy(List.of())
        );
        AtomicInteger stageCalls = new AtomicInteger();
        Stage counterStage = ctx -> { stageCalls.incrementAndGet(); return Stage.Result.CONTINUE; };
        EventDispatcher dispatcher = build(registry, specialty, List.of(counterStage));

        Player player = server.addPlayer();
        // 専業未選択
        MatchContext ctx = MatchContext.builder().entity(NamespacedKey.minecraft("zombie")).build();
        dispatcher.dispatch(player, ActionType.ENTITY_KILLED, ctx);

        assertEquals(0, stageCalls.get());
    }

    @Test
    void matchingActionInvokesPipeline() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(makeCombatJob()));
        InMemoryRepo repo = new InMemoryRepo();
        InMemoryHistory history = new InMemoryHistory();
        SpecialtyService specialty = new SpecialtyService(
                plugin, repo, history, registry,
                new CooldownPolicy(List.of())
        );
        AtomicInteger stageCalls = new AtomicInteger();
        Stage counterStage = ctx -> { stageCalls.incrementAndGet(); return Stage.Result.CONTINUE; };
        EventDispatcher dispatcher = build(registry, specialty, List.of(counterStage));

        Player player = server.addPlayer();
        specialty.loadPlayer(player.getUniqueId());
        specialty.select(player, new JobId("combat"));

        MatchContext ctx = MatchContext.builder().entity(NamespacedKey.minecraft("zombie")).build();
        dispatcher.dispatch(player, ActionType.ENTITY_KILLED, ctx);

        assertEquals(1, stageCalls.get());
    }

    @Test
    void nonMatchingActionDoesNotInvokePipeline() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(makeCombatJob()));
        InMemoryRepo repo = new InMemoryRepo();
        InMemoryHistory history = new InMemoryHistory();
        SpecialtyService specialty = new SpecialtyService(
                plugin, repo, history, registry,
                new CooldownPolicy(List.of())
        );
        AtomicInteger stageCalls = new AtomicInteger();
        Stage counterStage = ctx -> { stageCalls.incrementAndGet(); return Stage.Result.CONTINUE; };
        EventDispatcher dispatcher = build(registry, specialty, List.of(counterStage));

        Player player = server.addPlayer();
        specialty.loadPlayer(player.getUniqueId());
        specialty.select(player, new JobId("combat"));

        // combat 選択で block_broken → 何も起こらない
        MatchContext ctx = MatchContext.builder().block(NamespacedKey.minecraft("stone")).build();
        dispatcher.dispatch(player, ActionType.BLOCK_BROKEN, ctx);

        assertEquals(0, stageCalls.get());
    }

    @Test
    void haltStageStopsPipeline() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(makeCombatJob()));
        InMemoryRepo repo = new InMemoryRepo();
        InMemoryHistory history = new InMemoryHistory();
        SpecialtyService specialty = new SpecialtyService(
                plugin, repo, history, registry,
                new CooldownPolicy(List.of())
        );
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        List<Stage> stages = List.of(
                ctx -> { firstCalls.incrementAndGet(); return Stage.Result.HALT; },
                ctx -> { secondCalls.incrementAndGet(); return Stage.Result.CONTINUE; }
        );
        EventDispatcher dispatcher = build(registry, specialty, stages);

        Player player = server.addPlayer();
        specialty.loadPlayer(player.getUniqueId());
        specialty.select(player, new JobId("combat"));
        MatchContext ctx = MatchContext.builder().entity(NamespacedKey.minecraft("zombie")).build();
        dispatcher.dispatch(player, ActionType.ENTITY_KILLED, ctx);

        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());
    }
}
