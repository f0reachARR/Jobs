package me.f0reach.jobs.placeholder;

import me.f0reach.jobs.api.specialty.PlayerJobService;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.registry.JobRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PlaceholderAPI 拡張から呼び出す解決ロジック。PAPI に非依存にすることで JUnit で検証できる。
 *
 * <p>プレイヤーの現在職業は {@link PlayerJobService#getCurrentJobId(UUID)} のキャッシュ経由で
 * 同期取得する。オンライン中のみキャッシュに乗るため、オフラインは常に「未選択」として扱う。
 */
public final class JobPlaceholderResolver {

    private final PlayerJobService playerJobService;
    private final JobRegistry jobRegistry;

    public JobPlaceholderResolver(PlayerJobService playerJobService, JobRegistry jobRegistry) {
        this.playerJobService = Objects.requireNonNull(playerJobService, "playerJobService");
        this.jobRegistry = Objects.requireNonNull(jobRegistry, "jobRegistry");
    }

    /**
     * 未知のパラメータに対しては null を返す。PAPI はこれを invalid placeholder として扱う。
     * 対応済みパラメータで値が無い場合（未選択・display_name 解決失敗）は空文字を返す。
     */
    public @Nullable String resolve(@NotNull UUID player, @NotNull String param) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(param, "param");
        Optional<String> current = playerJobService.getCurrentJobId(player);
        return switch (param.toLowerCase(java.util.Locale.ROOT)) {
            case "current_id" -> current.orElse("");
            case "current_name" -> current.flatMap(this::displayName).orElse("");
            case "has_job" -> current.isPresent() ? "true" : "false";
            default -> null;
        };
    }

    private Optional<String> displayName(String jobIdValue) {
        JobId id;
        try {
            id = new JobId(jobIdValue);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return jobRegistry.get(id).map(JobDefinition::displayName);
    }
}
