package me.f0reach.jobs.specialty;

import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import me.f0reach.jobs.registry.JobRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialtyServiceTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private JobDefinition job(String id) {
        return new JobDefinition(
                new JobId(id),
                "Job " + id,
                NamespacedKey.minecraft("stone"),
                List.of(),
                VarietyPenaltyConfig.disabled(),
                AntiAutomationConfig.empty()
        );
    }

    private static final class InMemoryPlayerJobRepository implements PlayerJobRepository {
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

    private SpecialtyService buildService(PlayerJobRepository repo, JobRegistry reg, Clock clock, Duration cooldown) {
        CooldownPolicy policy = new CooldownPolicy(
                List.of(new me.f0reach.jobs.config.PluginConfig.ChangePolicy(
                        true,
                        me.f0reach.jobs.config.PluginConfig.WithinCondition.none(),
                        cooldown
                )),
                ZoneOffset.UTC
        );
        return new SpecialtyService(plugin, repo, reg, policy, clock);
    }

    @Test
    void firstSelectWritesRowAndFiresEvent() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        SpecialtyService service = buildService(repo, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        SpecialtyChangeResult result = service.select(player, new JobId("combat"));

        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
        assertTrue(((SpecialtyChangeResult.Success) result).initial());
        assertEquals(1, repo.rows.size());
        assertEquals("combat", service.currentJob(player.getUniqueId()).get().value());
    }

    @Test
    void secondSelectIsNoChange() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        SpecialtyService service = buildService(repo, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        service.select(player, new JobId("combat"));
        SpecialtyChangeResult second = service.select(player, new JobId("combat"));

        assertInstanceOf(SpecialtyChangeResult.NoChange.class, second);
    }

    @Test
    void changeWithinCooldownReturnsCooldownRemaining() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();

        // 固定 clock で最初の select を過去に済ませ、change 呼び出し時に cooldown 未経過にする
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Clock atT0 = Clock.fixed(t0, ZoneOffset.UTC);
        SpecialtyService serviceT0 = buildService(repo, registry, atT0, Duration.ofDays(5));
        Player player = server.addPlayer();
        serviceT0.loadPlayer(player.getUniqueId());
        serviceT0.select(player, new JobId("combat"));

        // 1 時間後に change を試みる → 5d cooldown 内
        Clock atT1 = Clock.fixed(t0.plus(Duration.ofHours(1)), ZoneOffset.UTC);
        SpecialtyService serviceT1 = buildService(repo, registry, atT1, Duration.ofDays(5));
        serviceT1.loadPlayer(player.getUniqueId());
        SpecialtyChangeResult result = serviceT1.change(player, new JobId("mining"));

        assertInstanceOf(SpecialtyChangeResult.CooldownRemaining.class, result);
        SpecialtyChangeResult.CooldownRemaining c = (SpecialtyChangeResult.CooldownRemaining) result;
        assertTrue(c.remaining().toHours() > 0);
    }

    @Test
    void changeAfterCooldownSucceeds() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService serviceT0 = buildService(repo, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofHours(1));
        Player player = server.addPlayer();
        serviceT0.loadPlayer(player.getUniqueId());
        serviceT0.select(player, new JobId("combat"));

        SpecialtyService serviceT1 = buildService(repo, registry,
                Clock.fixed(t0.plus(Duration.ofHours(2)), ZoneOffset.UTC), Duration.ofHours(1));
        serviceT1.loadPlayer(player.getUniqueId());
        SpecialtyChangeResult result = serviceT1.change(player, new JobId("mining"));

        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
        assertEquals("mining", serviceT1.currentJob(player.getUniqueId()).get().value());
        assertEquals(2, repo.rows.size());
    }

    @Test
    void unknownJobReturnsUnknownJob() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        SpecialtyService service = buildService(repo, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        SpecialtyChangeResult result = service.select(player, new JobId("nonexistent"));

        assertInstanceOf(SpecialtyChangeResult.UnknownJob.class, result);
    }
}
