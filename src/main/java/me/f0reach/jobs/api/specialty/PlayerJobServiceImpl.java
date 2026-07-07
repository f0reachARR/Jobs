package me.f0reach.jobs.api.specialty;

import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import me.f0reach.jobs.specialty.SpecialtyChangeResult;
import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@link PlayerJobService} の実装。{@link SpecialtyService} を薄くラップする。
 *
 * <p>公開 API は job id を {@link String} で扱うため、ここで {@link JobId} と相互変換する。
 * 妥当性が壊れている job id を渡された場合は {@link JobChangeResult.UnknownJob} を返す。
 */
public final class PlayerJobServiceImpl implements PlayerJobService {

    private final SpecialtyService specialtyService;
    private final PlayerJobRepository playerJobRepository;
    private final AsyncExecutor asyncExecutor;

    public PlayerJobServiceImpl(
            SpecialtyService specialtyService,
            PlayerJobRepository playerJobRepository,
            AsyncExecutor asyncExecutor
    ) {
        this.specialtyService = specialtyService;
        this.playerJobRepository = playerJobRepository;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public Optional<String> getCurrentJobId(@NotNull UUID player) {
        Objects.requireNonNull(player, "player");
        return specialtyService.currentJob(player).map(JobId::value);
    }

    @Override
    public CompletableFuture<Optional<String>> fetchCurrentJobId(@NotNull UUID player) {
        Objects.requireNonNull(player, "player");
        return asyncExecutor.supplyAsync(() -> playerJobRepository.find(player).map(PlayerJobRow::jobId));
    }

    @Override
    public Optional<Instant> nextChangeAvailableAt(@NotNull UUID player) {
        Objects.requireNonNull(player, "player");
        return specialtyService.nextAvailableAt(player);
    }

    @Override
    public @NotNull JobChangeResult changeAsPlayer(@NotNull Player player, @NotNull String jobId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(jobId, "jobId");
        JobId parsed = parseJobIdOrNull(jobId);
        if (parsed == null) {
            return new JobChangeResult.UnknownJob(jobId);
        }
        SpecialtyChangeResult internal = specialtyService.change(player, parsed);
        return map(internal);
    }

    @Override
    public @NotNull CompletableFuture<JobChangeResult> setBySystem(
            @NotNull UUID player, @NotNull String jobId, @NotNull String actorTag
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(actorTag, "actorTag");
        JobId parsed = parseJobIdOrNull(jobId);
        if (parsed == null) {
            return CompletableFuture.completedFuture(new JobChangeResult.UnknownJob(jobId));
        }
        return asyncExecutor.supplyAsync(() -> map(specialtyService.setBySystem(player, parsed)));
    }

    private static JobId parseJobIdOrNull(String jobId) {
        try {
            return new JobId(jobId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JobChangeResult map(SpecialtyChangeResult internal) {
        return switch (internal) {
            case SpecialtyChangeResult.Success s -> new JobChangeResult.Success(
                    s.previous() == null ? null : s.previous().value(),
                    s.next().value(),
                    s.changedAt(),
                    s.initial());
            case SpecialtyChangeResult.CooldownRemaining c ->
                    new JobChangeResult.CooldownRemaining(c.remaining(), c.nextAvailable());
            case SpecialtyChangeResult.UnknownJob u ->
                    new JobChangeResult.UnknownJob(u.requested().value());
            case SpecialtyChangeResult.NoChange n -> new JobChangeResult.NoChange();
        };
    }
}
