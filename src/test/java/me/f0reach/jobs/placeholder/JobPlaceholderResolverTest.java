package me.f0reach.jobs.placeholder;

import me.f0reach.jobs.api.specialty.JobChangeResult;
import me.f0reach.jobs.api.specialty.PlayerJobService;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.registry.JobRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JobPlaceholderResolverTest {

    private ServerMock server;
    private JobRegistry jobRegistry;
    private FakePlayerJobService playerJobService;
    private JobPlaceholderResolver resolver;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        jobRegistry = new JobRegistry();
        jobRegistry.loadAll(List.of(
                new JobDefinition(
                        new JobId("combat"), "討伐", null,
                        NamespacedKey.minecraft("iron_sword"),
                        List.of(), VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty())
        ));
        playerJobService = new FakePlayerJobService();
        resolver = new JobPlaceholderResolver(playerJobService, jobRegistry);
        Player player = server.addPlayer();
        playerId = player.getUniqueId();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void unselectedPlayerReturnsEmptyForKnownParams() {
        assertEquals("", resolver.resolve(playerId, "current_id"));
        assertEquals("", resolver.resolve(playerId, "current_name"));
        assertEquals("false", resolver.resolve(playerId, "has_job"));
    }

    @Test
    void selectedPlayerReturnsIdAndDisplayName() {
        playerJobService.set(playerId, "combat");
        assertEquals("combat", resolver.resolve(playerId, "current_id"));
        assertEquals("討伐", resolver.resolve(playerId, "current_name"));
        assertEquals("true", resolver.resolve(playerId, "has_job"));
    }

    @Test
    void unknownParamReturnsNull() {
        playerJobService.set(playerId, "combat");
        assertNull(resolver.resolve(playerId, "unknown_field"));
    }

    @Test
    void paramIsCaseInsensitive() {
        playerJobService.set(playerId, "combat");
        assertEquals("combat", resolver.resolve(playerId, "CURRENT_ID"));
        assertEquals("討伐", resolver.resolve(playerId, "Current_Name"));
    }

    @Test
    void unknownJobInRegistryYieldsEmptyName() {
        playerJobService.set(playerId, "ghost");
        assertEquals("ghost", resolver.resolve(playerId, "current_id"));
        assertEquals("", resolver.resolve(playerId, "current_name"));
        assertEquals("true", resolver.resolve(playerId, "has_job"));
    }

    @Test
    void invalidJobIdSyntaxYieldsEmptyName() {
        playerJobService.set(playerId, "NOT-A-VALID-ID");
        assertEquals("NOT-A-VALID-ID", resolver.resolve(playerId, "current_id"));
        assertEquals("", resolver.resolve(playerId, "current_name"));
    }

    private static final class FakePlayerJobService implements PlayerJobService {
        private final Map<UUID, String> jobs = new HashMap<>();

        void set(UUID player, String jobId) {
            jobs.put(player, jobId);
        }

        @Override
        public Optional<String> getCurrentJobId(@NotNull UUID player) {
            return Optional.ofNullable(jobs.get(player));
        }

        @Override
        public CompletableFuture<Optional<String>> fetchCurrentJobId(@NotNull UUID player) {
            return CompletableFuture.completedFuture(getCurrentJobId(player));
        }

        @Override
        public Optional<Instant> nextChangeAvailableAt(@NotNull UUID player) {
            return Optional.empty();
        }

        @Override
        public @NotNull JobChangeResult changeAsPlayer(@NotNull Player player, @NotNull String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull CompletableFuture<JobChangeResult> setBySystem(
                @NotNull UUID player, @NotNull String jobId, @NotNull String actorTag) {
            throw new UnsupportedOperationException();
        }
    }
}
