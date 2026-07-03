package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.config.PluginConfig;
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
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import me.f0reach.jobs.pipeline.stage.SpecialtyStage;
import me.f0reach.jobs.registry.JobRegistry;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpecialtyStageTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private JobDefinition makeJob(String id) {
        MatchCriteria c = new MatchCriteria.EntityKilled(
                new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        RewardEntry entry = new RewardEntry(
                ActionType.ENTITY_KILLED, c, new RewardAmount.Fixed(10.0), null,
                new ActionKey("kill:minecraft:zombie"));
        return new JobDefinition(
                new JobId(id), "Job " + id, null, NamespacedKey.minecraft("iron_sword"),
                List.of(entry), VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty()
        );
    }

    private PipelineContext ctxFor(Player player, JobDefinition job) {
        DetectedAction action = new DetectedAction(
                player, job.id(), job.rewards().get(0), job.rewards().get(0).derivedKey(),
                1, SourceFlags.none(), DetectionSubject.empty());
        return new PipelineContext(action, job, Instant.now());
    }

    private SpecialtyService makeService() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(makeJob("combat"), makeJob("mining")));
        CooldownPolicy policy = new CooldownPolicy(
                List.of(new PluginConfig.ChangePolicy(
                        true, PluginConfig.WithinCondition.none(), Duration.ofDays(5))),
                ZoneOffset.UTC);
        return new SpecialtyService(plugin, new InMemoryRepo(), registry, policy, Clock.systemUTC());
    }

    @Test
    void haltsWhenPlayerHasNoSpecialty() {
        SpecialtyService service = makeService();
        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        SpecialtyStage stage = new SpecialtyStage(service);

        Stage.Result r = stage.execute(ctxFor(player, makeJob("combat")));

        assertEquals(Stage.Result.HALT, r);
    }

    @Test
    void haltsWhenSpecialtyDiffersFromMatchedJob() {
        SpecialtyService service = makeService();
        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        service.select(player, new JobId("mining"));
        SpecialtyStage stage = new SpecialtyStage(service);

        Stage.Result r = stage.execute(ctxFor(player, makeJob("combat")));

        assertEquals(Stage.Result.HALT, r);
    }

    @Test
    void continuesWhenSpecialtyMatchesMatchedJob() {
        SpecialtyService service = makeService();
        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        service.select(player, new JobId("combat"));
        SpecialtyStage stage = new SpecialtyStage(service);

        Stage.Result r = stage.execute(ctxFor(player, makeJob("combat")));

        assertEquals(Stage.Result.CONTINUE, r);
    }

    @Test
    void bypassLetsUnselectedPlayerThrough() {
        SpecialtyService service = makeService();
        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        player.addAttachment(plugin, "jobs.bypass.specialty", true);
        SpecialtyStage stage = new SpecialtyStage(service);

        Stage.Result r = stage.execute(ctxFor(player, makeJob("combat")));

        assertEquals(Stage.Result.CONTINUE, r);
    }

    @Test
    void bypassLetsPlayerEarnFromNonSpecialtyJob() {
        SpecialtyService service = makeService();
        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        service.select(player, new JobId("mining"));
        player.addAttachment(plugin, "jobs.bypass.specialty", true);
        SpecialtyStage stage = new SpecialtyStage(service);

        Stage.Result r = stage.execute(ctxFor(player, makeJob("combat")));

        assertEquals(Stage.Result.CONTINUE, r);
    }

    private static final class InMemoryRepo implements PlayerJobRepository {
        record Entry(UUID player, String jobId, Instant selectedAt) {}
        final List<Entry> rows = new ArrayList<>();

        @Override
        public Optional<PlayerJobRow> findCurrent(UUID player) {
            Entry latest = null;
            for (Entry e : rows) {
                if (!e.player().equals(player)) continue;
                if (latest == null || e.selectedAt().isAfter(latest.selectedAt())) latest = e;
            }
            return latest == null ? Optional.empty()
                    : Optional.of(new PlayerJobRow(latest.player(), latest.jobId(), latest.selectedAt()));
        }

        @Override
        public void insertSelection(UUID player, String jobId, Instant selectedAt) {
            rows.add(new Entry(player, jobId, selectedAt));
        }

        @Override
        public Optional<Instant> lastChangedAt(UUID player) {
            return findCurrent(player).map(PlayerJobRow::selectedAt);
        }
    }
}
