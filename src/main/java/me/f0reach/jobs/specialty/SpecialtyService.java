package me.f0reach.jobs.specialty;

import me.f0reach.jobs.api.event.JobSpecialtyChangedEvent;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.PlayerJobRow;
import me.f0reach.jobs.registry.JobRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 現在の専業取得、初回選択、変更を扱う。
 *
 * spec/06-public-api.md 「JobSpecialtyChangedEvent」および
 * docs/plan/class-structure.md 「specialty」を参照。
 *
 * 起動時にプレイヤーログインで player_job の最新行をキャッシュに読み、
 * 以降は cache とリポジトリを同期して扱う。
 */
public final class SpecialtyService {

    static final String BYPASS_COOLDOWN = "jobs.bypass.cooldown";

    private final Plugin plugin;
    private final PlayerJobRepository repository;
    private final JobRegistry jobRegistry;
    private final CooldownPolicy cooldownPolicy;
    private final Clock clock;

    /** 現在の専業。null は未選択。 */
    private final ConcurrentHashMap<UUID, JobId> currentCache = new ConcurrentHashMap<>();
    /** 最終変更時刻。null はまだ変更履歴がない。 */
    private final ConcurrentHashMap<UUID, Instant> lastChangedCache = new ConcurrentHashMap<>();

    public SpecialtyService(
            Plugin plugin,
            PlayerJobRepository repository,
            JobRegistry jobRegistry,
            CooldownPolicy cooldownPolicy
    ) {
        this(plugin, repository, jobRegistry, cooldownPolicy, Clock.systemUTC());
    }

    public SpecialtyService(
            Plugin plugin,
            PlayerJobRepository repository,
            JobRegistry jobRegistry,
            CooldownPolicy cooldownPolicy,
            Clock clock
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.jobRegistry = jobRegistry;
        this.cooldownPolicy = cooldownPolicy;
        this.clock = clock;
    }

    /** プレイヤーログイン時に cache を warm する。 */
    public void loadPlayer(UUID player) {
        repository.findCurrent(player).ifPresentOrElse(
                row -> {
                    try {
                        currentCache.put(player, new JobId(row.jobId()));
                        lastChangedCache.put(player, row.selectedAt());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning(
                                "player_job for " + player + " has invalid job id: " + row.jobId()
                        );
                    }
                },
                () -> {
                    currentCache.remove(player);
                    lastChangedCache.remove(player);
                }
        );
    }

    public void unloadPlayer(UUID player) {
        currentCache.remove(player);
        lastChangedCache.remove(player);
    }

    /** cache から現在の専業を返す。cache miss は Optional.empty()。 */
    public Optional<JobId> currentJob(UUID player) {
        return Optional.ofNullable(currentCache.get(player));
    }

    /** 初回選択。既に選択済みなら NoChange を返す。 */
    public SpecialtyChangeResult select(Player player, JobId jobId) {
        if (!jobRegistry.get(jobId).isPresent()) {
            return new SpecialtyChangeResult.UnknownJob(jobId);
        }
        if (currentCache.containsKey(player.getUniqueId())) {
            return new SpecialtyChangeResult.NoChange();
        }
        Instant now = Instant.now(clock);
        repository.insertSelection(player.getUniqueId(), jobId.value(), now);
        currentCache.put(player.getUniqueId(), jobId);
        lastChangedCache.put(player.getUniqueId(), now);
        fireEvent(player, null, jobId, now);
        return new SpecialtyChangeResult.Success(null, jobId, now, true);
    }

    /**
     * 変更。cooldown 内なら CooldownRemaining、同 job なら NoChange。
     */
    public SpecialtyChangeResult change(Player player, JobId newJobId) {
        if (!jobRegistry.get(newJobId).isPresent()) {
            return new SpecialtyChangeResult.UnknownJob(newJobId);
        }
        UUID uuid = player.getUniqueId();
        JobId current = currentCache.get(uuid);
        if (current == null) {
            // まだ未選択なら change ではなく select 動線を促す扱いにする。
            return select(player, newJobId);
        }
        if (current.equals(newJobId)) {
            return new SpecialtyChangeResult.NoChange();
        }
        Instant now = Instant.now(clock);
        // jobs.bypass.cooldown を持つプレイヤーはクールダウン判定をスキップする。
        // nextAvailableAt が返す値は履歴通り (last + cooldown) のままなので、
        // /jobs status の表示は変わらない。
        if (!player.hasPermission(BYPASS_COOLDOWN)) {
            Duration cooldown = cooldownPolicy.currentCooldown(now);
            Instant lastChanged = lastChangedCache.get(uuid);
            if (lastChanged != null && !cooldown.isZero()) {
                Instant nextAvailable = lastChanged.plus(cooldown);
                if (nextAvailable.isAfter(now)) {
                    return new SpecialtyChangeResult.CooldownRemaining(
                            Duration.between(now, nextAvailable), nextAvailable
                    );
                }
            }
        }
        repository.insertSelection(uuid, newJobId.value(), now);
        currentCache.put(uuid, newJobId);
        lastChangedCache.put(uuid, now);
        fireEvent(player, current, newJobId, now);
        return new SpecialtyChangeResult.Success(current, newJobId, now, false);
    }

    /** 次回変更可能時刻。lastChanged が無い場合は Optional.empty()。 */
    public Optional<Instant> nextAvailableAt(UUID player) {
        Instant last = lastChangedCache.get(player);
        if (last == null) return Optional.empty();
        Duration cooldown = cooldownPolicy.currentCooldown(Instant.now(clock));
        return Optional.of(last.plus(cooldown));
    }

    /** 変更履歴を持たない（未選択）プレイヤーか。 */
    public boolean isFirstTime(UUID player) {
        return !currentCache.containsKey(player);
    }

    /** ログイン時に cache に読み込むための同期ヘルパ。 */
    public Optional<PlayerJobRow> loadRow(UUID player) {
        return repository.findCurrent(player);
    }

    private void fireEvent(Player player, JobId previous, JobId next, Instant at) {
        JobSpecialtyChangedEvent event = new JobSpecialtyChangedEvent(
                player, previous == null ? null : previous.value(), next.value(), at
        );
        // Bukkit イベントは main thread から発火する。
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(event));
        }
    }
}
