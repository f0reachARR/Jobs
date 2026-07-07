package me.f0reach.jobs.api.specialty;

import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.persistence.dto.Actor;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.specialty.CooldownPolicy;
import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.testsupport.InMemoryPlayerJobHistoryRepository;
import me.f0reach.jobs.testsupport.InMemoryPlayerJobRepository;
import me.f0reach.jobs.util.AsyncExecutor;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerJobServiceImplTest {

    private ServerMock server;
    private Plugin plugin;
    private AsyncExecutor asyncExecutor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Jobs");
        asyncExecutor = new AsyncExecutor(plugin);
    }

    @AfterEach
    void tearDown() {
        asyncExecutor.shutdown();
        MockBukkit.unmock();
    }

    private JobDefinition job(String id) {
        return new JobDefinition(
                new JobId(id),
                "Job " + id,
                null,
                NamespacedKey.minecraft("stone"),
                List.of(),
                VarietyPenaltyConfig.disabled(),
                AntiAutomationConfig.empty()
        );
    }

    private SpecialtyService buildService(
            InMemoryPlayerJobRepository repo,
            InMemoryPlayerJobHistoryRepository history,
            JobRegistry reg,
            Clock clock,
            Duration cooldown
    ) {
        CooldownPolicy policy = new CooldownPolicy(
                List.of(new PluginConfig.ChangePolicy(
                        true, PluginConfig.WithinCondition.none(), cooldown)),
                ZoneOffset.UTC
        );
        return new SpecialtyService(plugin, repo, history, reg, policy, clock);
    }

    private static <T> T await(java.util.concurrent.CompletableFuture<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getCurrentJobIdReturnsCacheValueForOnlinePlayer() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        var repo = new InMemoryPlayerJobRepository();
        var history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        service.select(player, new JobId("combat"));

        PlayerJobServiceImpl api = new PlayerJobServiceImpl(service, repo, asyncExecutor);
        assertEquals(Optional.of("combat"), api.getCurrentJobId(player.getUniqueId()));
    }

    @Test
    void fetchCurrentJobIdReadsFromRepositoryEvenWhenOffline() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        var repo = new InMemoryPlayerJobRepository();
        var history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        // オフライン相当の UUID に直接 DB を仕込む
        UUID offline = UUID.randomUUID();
        repo.upsert(offline, "combat", Instant.now());

        PlayerJobServiceImpl api = new PlayerJobServiceImpl(service, repo, asyncExecutor);
        assertTrue(api.getCurrentJobId(offline).isEmpty(), "キャッシュ経由はオフライン empty");
        assertEquals(Optional.of("combat"), await(api.fetchCurrentJobId(offline)));
    }

    @Test
    void changeAsPlayerReturnsCooldownRemainingWithinWindow() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        var repo = new InMemoryPlayerJobRepository();
        var history = new InMemoryPlayerJobHistoryRepository();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService serviceT0 = buildService(repo, history, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofDays(5));

        Player player = server.addPlayer();
        serviceT0.loadPlayer(player.getUniqueId());
        serviceT0.select(player, new JobId("combat"));

        SpecialtyService serviceT1 = buildService(repo, history, registry,
                Clock.fixed(t0.plus(Duration.ofHours(1)), ZoneOffset.UTC), Duration.ofDays(5));
        serviceT1.loadPlayer(player.getUniqueId());
        PlayerJobServiceImpl api = new PlayerJobServiceImpl(serviceT1, repo, asyncExecutor);

        JobChangeResult result = api.changeAsPlayer(player, "mining");
        assertInstanceOf(JobChangeResult.CooldownRemaining.class, result);
    }

    @Test
    void changeAsPlayerSuccessAfterCooldown() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        var repo = new InMemoryPlayerJobRepository();
        var history = new InMemoryPlayerJobHistoryRepository();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService serviceT0 = buildService(repo, history, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofHours(1));

        Player player = server.addPlayer();
        serviceT0.loadPlayer(player.getUniqueId());
        serviceT0.select(player, new JobId("combat"));

        SpecialtyService serviceT1 = buildService(repo, history, registry,
                Clock.fixed(t0.plus(Duration.ofHours(2)), ZoneOffset.UTC), Duration.ofHours(1));
        serviceT1.loadPlayer(player.getUniqueId());
        PlayerJobServiceImpl api = new PlayerJobServiceImpl(serviceT1, repo, asyncExecutor);

        JobChangeResult result = api.changeAsPlayer(player, "mining");
        assertInstanceOf(JobChangeResult.Success.class, result);
        JobChangeResult.Success s = (JobChangeResult.Success) result;
        assertEquals("combat", s.previousJobId());
        assertEquals("mining", s.newJobId());
        assertTrue(!s.initial());
    }

    @Test
    void setBySystemWorksForOfflinePlayer() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        var repo = new InMemoryPlayerJobRepository();
        var history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        PlayerJobServiceImpl api = new PlayerJobServiceImpl(service, repo, asyncExecutor);
        UUID offline = UUID.randomUUID();

        JobChangeResult result = await(api.setBySystem(offline, "combat", "quest-plugin"));
        assertInstanceOf(JobChangeResult.Success.class, result);
        JobChangeResult.Success s = (JobChangeResult.Success) result;
        assertNull(s.previousJobId());
        assertTrue(s.initial());
        assertEquals("combat", s.newJobId());

        assertEquals(1, history.rows.size());
        assertEquals(Actor.SYSTEM, history.rows.get(0).actor());
    }

    @Test
    void setBySystemReturnsUnknownJobForMissingJobId() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        var repo = new InMemoryPlayerJobRepository();
        var history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        PlayerJobServiceImpl api = new PlayerJobServiceImpl(service, repo, asyncExecutor);
        JobChangeResult result = await(api.setBySystem(UUID.randomUUID(), "no_such_job", "tag"));
        assertInstanceOf(JobChangeResult.UnknownJob.class, result);
        assertEquals("no_such_job", ((JobChangeResult.UnknownJob) result).requestedJobId());
    }

    @Test
    void setBySystemReturnsUnknownJobForMalformedJobId() {
        // JobId の regex ([a-z0-9_]+) を外れる string は Impl 側で UnknownJob にマップする。
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        var repo = new InMemoryPlayerJobRepository();
        var history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        PlayerJobServiceImpl api = new PlayerJobServiceImpl(service, repo, asyncExecutor);
        JobChangeResult result = await(api.setBySystem(UUID.randomUUID(), "Bad-Id", "tag"));
        assertInstanceOf(JobChangeResult.UnknownJob.class, result);
        assertEquals("Bad-Id", ((JobChangeResult.UnknownJob) result).requestedJobId());
    }

    @Test
    void changeAsPlayerReturnsUnknownJobForMalformedJobId() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        var repo = new InMemoryPlayerJobRepository();
        var history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        PlayerJobServiceImpl api = new PlayerJobServiceImpl(service, repo, asyncExecutor);

        JobChangeResult result = api.changeAsPlayer(player, "Bad-Id");
        assertInstanceOf(JobChangeResult.UnknownJob.class, result);
    }
}
