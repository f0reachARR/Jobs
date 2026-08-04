package me.f0reach.jobs.specialty;

import me.f0reach.jobs.Permissions;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.persistence.dto.Actor;
import me.f0reach.jobs.persistence.dto.PlayerJobHistoryRow;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.testsupport.FixedFirstJoinProvider;
import me.f0reach.jobs.testsupport.InMemoryPlayerJobHistoryRepository;
import me.f0reach.jobs.testsupport.InMemoryPlayerJobRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                null,
                NamespacedKey.minecraft("stone"),
                List.of(),
                VarietyPenaltyConfig.disabled(),
                AntiAutomationConfig.empty()
        );
    }

    private SpecialtyService buildService(
            me.f0reach.jobs.persistence.PlayerJobRepository repo,
            me.f0reach.jobs.persistence.PlayerJobHistoryRepository history,
            JobRegistry reg,
            Clock clock,
            Duration cooldown
    ) {
        CooldownPolicy policy = new CooldownPolicy(
                List.of(new me.f0reach.jobs.config.PluginConfig.ChangePolicy(
                        true,
                        me.f0reach.jobs.config.PluginConfig.WithinCondition.none(),
                        cooldown
                )),
                ZoneOffset.UTC
        );
        return new SpecialtyService(
                plugin, repo, history, reg, policy, FixedFirstJoinProvider.unknown(), clock);
    }

    /** 初参加から firstJoinWithin 以内なら newbie、それ以外は fallback を返す policy で組む。 */
    private SpecialtyService buildFirstJoinService(
            me.f0reach.jobs.persistence.PlayerJobRepository repo,
            me.f0reach.jobs.persistence.PlayerJobHistoryRepository history,
            JobRegistry reg,
            Clock clock,
            Instant firstJoinAt,
            Duration firstJoinWithin,
            Duration newbie,
            Duration fallback
    ) {
        CooldownPolicy policy = new CooldownPolicy(
                List.of(
                        new me.f0reach.jobs.config.PluginConfig.ChangePolicy(
                                false,
                                new me.f0reach.jobs.config.PluginConfig.WithinCondition(
                                        List.of(), firstJoinWithin),
                                newbie
                        ),
                        new me.f0reach.jobs.config.PluginConfig.ChangePolicy(
                                true,
                                me.f0reach.jobs.config.PluginConfig.WithinCondition.none(),
                                fallback
                        )
                ),
                ZoneOffset.UTC
        );
        return new SpecialtyService(
                plugin, repo, history, reg, policy,
                FixedFirstJoinProvider.at(firstJoinAt), clock);
    }

    @Test
    void firstSelectWritesRowAndFiresEvent() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        SpecialtyChangeResult result = service.select(player, new JobId("combat"));

        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
        assertTrue(((SpecialtyChangeResult.Success) result).initial());
        assertEquals(1, repo.rows.size());
        assertEquals("combat", service.currentJob(player.getUniqueId()).get().value());
        assertEquals(1, history.rows.size());
        PlayerJobHistoryRow row = history.rows.get(0);
        assertEquals("combat", row.jobId());
        assertNull(row.previousJobId());
        assertEquals(Actor.PLAYER, row.actor());
    }

    @Test
    void secondSelectIsNoChange() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

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
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();

        // 固定 clock で最初の select を過去に済ませ、change 呼び出し時に cooldown 未経過にする
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Clock atT0 = Clock.fixed(t0, ZoneOffset.UTC);
        SpecialtyService serviceT0 = buildService(repo, history, registry, atT0, Duration.ofDays(5));
        Player player = server.addPlayer();
        serviceT0.loadPlayer(player.getUniqueId());
        serviceT0.select(player, new JobId("combat"));

        // 1 時間後に change を試みる → 5d cooldown 内
        Clock atT1 = Clock.fixed(t0.plus(Duration.ofHours(1)), ZoneOffset.UTC);
        SpecialtyService serviceT1 = buildService(repo, history, registry, atT1, Duration.ofDays(5));
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
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService serviceT0 = buildService(repo, history, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofHours(1));
        Player player = server.addPlayer();
        serviceT0.loadPlayer(player.getUniqueId());
        serviceT0.select(player, new JobId("combat"));

        SpecialtyService serviceT1 = buildService(repo, history, registry,
                Clock.fixed(t0.plus(Duration.ofHours(2)), ZoneOffset.UTC), Duration.ofHours(1));
        serviceT1.loadPlayer(player.getUniqueId());
        SpecialtyChangeResult result = serviceT1.change(player, new JobId("mining"));

        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
        assertEquals("mining", serviceT1.currentJob(player.getUniqueId()).get().value());
        assertEquals(1, repo.rows.size(), "player_job は 1 player 1 row");
        assertEquals(2, history.rows.size(), "history には 2 行入る");
        assertEquals("combat", history.rows.get(1).previousJobId());
    }

    @Test
    void changeWithCooldownBypassPermissionSucceeds() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService serviceT0 = buildService(repo, history, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofDays(5));
        Player player = server.addPlayer();
        serviceT0.loadPlayer(player.getUniqueId());
        serviceT0.select(player, new JobId("combat"));

        // cooldown 未経過だが bypass 権限を持たせる
        player.addAttachment(plugin, Permissions.BYPASS_COOLDOWN, true);
        SpecialtyService serviceT1 = buildService(repo, history, registry,
                Clock.fixed(t0.plus(Duration.ofHours(1)), ZoneOffset.UTC), Duration.ofDays(5));
        serviceT1.loadPlayer(player.getUniqueId());
        SpecialtyChangeResult result = serviceT1.change(player, new JobId("mining"));

        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
        assertEquals("mining", serviceT1.currentJob(player.getUniqueId()).get().value());
        assertEquals(1, repo.rows.size());
        assertEquals(2, history.rows.size());
    }

    @Test
    void nextAvailableAtIgnoresBypassPermission() {
        // bypass はクールダウン判定だけをスキップし、履歴上の「次回変更可能時刻」は動かさない。
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService service = buildService(repo, history, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofDays(5));
        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        service.select(player, new JobId("combat"));
        player.addAttachment(plugin, Permissions.BYPASS_COOLDOWN, true);

        Optional<Instant> next = service.nextAvailableAt(player.getUniqueId());
        assertTrue(next.isPresent());
        assertEquals(t0.plus(Duration.ofDays(5)), next.get());
    }

    @Test
    void unknownJobReturnsUnknownJob() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        SpecialtyChangeResult result = service.select(player, new JobId("nonexistent"));

        assertInstanceOf(SpecialtyChangeResult.UnknownJob.class, result);
    }

    @Test
    void setForcedWritesAdminHistoryForOnlinePlayer() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player target = server.addPlayer();
        service.loadPlayer(target.getUniqueId());
        service.select(target, new JobId("combat"));
        UUID actor = UUID.randomUUID();

        SpecialtyChangeResult result = service.setForced(target.getUniqueId(), new JobId("mining"), actor);
        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
        assertEquals("mining", service.currentJob(target.getUniqueId()).get().value());
        assertEquals(2, history.rows.size());
        PlayerJobHistoryRow adminRow = history.rows.get(1);
        assertEquals(Actor.ADMIN, adminRow.actor());
        assertEquals(actor, adminRow.actorUuid());
        assertEquals("combat", adminRow.previousJobId());
    }

    @Test
    void setBySystemWritesSystemHistoryForOnlinePlayer() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        Player target = server.addPlayer();
        service.loadPlayer(target.getUniqueId());
        service.select(target, new JobId("combat"));

        SpecialtyChangeResult result = service.setBySystem(target.getUniqueId(), new JobId("mining"));
        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
        assertEquals("mining", service.currentJob(target.getUniqueId()).get().value());
        assertEquals(2, history.rows.size());
        PlayerJobHistoryRow row = history.rows.get(1);
        assertEquals(Actor.SYSTEM, row.actor());
        assertNull(row.actorUuid());
        assertEquals("combat", row.previousJobId());
    }

    @Test
    void setBySystemForOfflinePlayerSkipsCacheUpdate() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        SpecialtyService service = buildService(repo, history, registry, Clock.systemUTC(), Duration.ofDays(5));

        UUID offline = UUID.randomUUID();
        SpecialtyChangeResult result = service.setBySystem(offline, new JobId("combat"));

        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
        assertEquals(1, repo.rows.size(), "オフラインでも DB は書かれる");
        assertEquals(1, history.rows.size());
        assertEquals(Actor.SYSTEM, history.rows.get(0).actor());
        // オフラインなのでキャッシュには載らない
        assertTrue(service.currentJob(offline).isEmpty());
    }

    @Test
    void setBySystemIsCooldownAgnostic() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService service = buildService(repo, history, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofDays(5));

        Player target = server.addPlayer();
        service.loadPlayer(target.getUniqueId());
        service.select(target, new JobId("combat"));
        SpecialtyChangeResult result = service.setBySystem(target.getUniqueId(), new JobId("mining"));
        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
    }

    @Test
    void setForcedIsCooldownAgnostic() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService service = buildService(repo, history, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofDays(5));

        Player target = server.addPlayer();
        service.loadPlayer(target.getUniqueId());
        service.select(target, new JobId("combat"));
        // cooldown 未経過でも admin 経路は通る
        SpecialtyChangeResult result = service.setForced(
                target.getUniqueId(), new JobId("mining"), UUID.randomUUID());
        assertInstanceOf(SpecialtyChangeResult.Success.class, result);
    }

    @Test
    void newPlayerGetsFirstJoinCooldown() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();

        Instant firstJoin = Instant.parse("2026-01-01T00:00:00Z");
        // 初参加の 1 時間後に select、その 2 時間後に change → 初参加から 3h 経過で 72h 以内
        SpecialtyService atSelect = buildFirstJoinService(repo, history, registry,
                Clock.fixed(firstJoin.plus(Duration.ofHours(1)), ZoneOffset.UTC),
                firstJoin, Duration.ofHours(72), Duration.ofHours(1), Duration.ofDays(5));
        Player player = server.addPlayer();
        atSelect.loadPlayer(player.getUniqueId());
        atSelect.select(player, new JobId("combat"));

        SpecialtyService atChange = buildFirstJoinService(repo, history, registry,
                Clock.fixed(firstJoin.plus(Duration.ofHours(3)), ZoneOffset.UTC),
                firstJoin, Duration.ofHours(72), Duration.ofHours(1), Duration.ofDays(5));
        atChange.loadPlayer(player.getUniqueId());

        assertEquals(Duration.ofHours(1), atChange.currentCooldown(player.getUniqueId()));
        // select から 2 時間経っているので 1h cooldown は明けている
        assertInstanceOf(SpecialtyChangeResult.Success.class,
                atChange.change(player, new JobId("mining")));
    }

    @Test
    void veteranPlayerFallsBackToDefaultCooldown() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat"), job("mining")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();

        Instant firstJoin = Instant.parse("2026-01-01T00:00:00Z");
        Instant selectAt = firstJoin.plus(Duration.ofDays(30));
        SpecialtyService atSelect = buildFirstJoinService(repo, history, registry,
                Clock.fixed(selectAt, ZoneOffset.UTC),
                firstJoin, Duration.ofHours(72), Duration.ofHours(1), Duration.ofDays(5));
        Player player = server.addPlayer();
        atSelect.loadPlayer(player.getUniqueId());
        atSelect.select(player, new JobId("combat"));

        // 初参加から 72h を大きく超えているので default の 5d が効く
        SpecialtyService atChange = buildFirstJoinService(repo, history, registry,
                Clock.fixed(selectAt.plus(Duration.ofHours(3)), ZoneOffset.UTC),
                firstJoin, Duration.ofHours(72), Duration.ofHours(1), Duration.ofDays(5));
        atChange.loadPlayer(player.getUniqueId());

        assertEquals(Duration.ofDays(5), atChange.currentCooldown(player.getUniqueId()));
        assertInstanceOf(SpecialtyChangeResult.CooldownRemaining.class,
                atChange.change(player, new JobId("mining")));
        assertEquals(selectAt.plus(Duration.ofDays(5)),
                atChange.nextAvailableAt(player.getUniqueId()).orElseThrow());
    }

    @Test
    void crossingFirstJoinThresholdReEvaluatesPolicy() {
        // base は据え置きのまま policy を毎回引き直すので、しきい値を跨ぐと
        // 同じ base に対する nextAvailableAt が後退する (ADR-0022 の既知の副作用)。
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();

        Instant firstJoin = Instant.parse("2026-01-01T00:00:00Z");
        Instant selectAt = firstJoin.plus(Duration.ofHours(71));
        SpecialtyService atSelect = buildFirstJoinService(repo, history, registry,
                Clock.fixed(selectAt, ZoneOffset.UTC),
                firstJoin, Duration.ofHours(72), Duration.ofHours(1), Duration.ofDays(5));
        Player player = server.addPlayer();
        atSelect.loadPlayer(player.getUniqueId());
        atSelect.select(player, new JobId("combat"));

        assertEquals(selectAt.plus(Duration.ofHours(1)),
                atSelect.nextAvailableAt(player.getUniqueId()).orElseThrow());

        SpecialtyService afterThreshold = buildFirstJoinService(repo, history, registry,
                Clock.fixed(firstJoin.plus(Duration.ofHours(73)), ZoneOffset.UTC),
                firstJoin, Duration.ofHours(72), Duration.ofHours(1), Duration.ofDays(5));
        afterThreshold.loadPlayer(player.getUniqueId());

        assertEquals(selectAt.plus(Duration.ofDays(5)),
                afterThreshold.nextAvailableAt(player.getUniqueId()).orElseThrow());
    }

    @Test
    void resetCooldownClearsBaseWithoutHistoryEntry() {
        JobRegistry registry = new JobRegistry();
        registry.loadAll(List.of(job("combat")));
        InMemoryPlayerJobRepository repo = new InMemoryPlayerJobRepository();
        InMemoryPlayerJobHistoryRepository history = new InMemoryPlayerJobHistoryRepository();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        SpecialtyService service = buildService(repo, history, registry,
                Clock.fixed(t0, ZoneOffset.UTC), Duration.ofDays(5));

        Player player = server.addPlayer();
        service.loadPlayer(player.getUniqueId());
        service.select(player, new JobId("combat"));
        int historyBefore = history.rows.size();

        service.resetCooldown(player.getUniqueId());

        assertEquals(historyBefore, history.rows.size(), "reset-cooldown は履歴を残さない");
        assertEquals(Instant.EPOCH, repo.rows.get(player.getUniqueId()).cooldownBaseAt());
        // cache も EPOCH に落ちるので、次の nextAvailableAt は過去
        Instant next = service.nextAvailableAt(player.getUniqueId()).orElseThrow();
        assertTrue(next.isBefore(t0));
    }
}
