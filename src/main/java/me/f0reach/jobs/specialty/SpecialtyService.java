package me.f0reach.jobs.specialty;

import me.f0reach.jobs.Permissions;
import me.f0reach.jobs.api.event.JobSpecialtyChangedEvent;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.persistence.PlayerJobHistoryRepository;
import me.f0reach.jobs.persistence.PlayerJobRepository;
import me.f0reach.jobs.persistence.dto.Actor;
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
 * 現在の専業取得、初回選択、変更、および管理系の強制付与・cooldown リセットを扱う。
 *
 * spec/06-public-api.md 「JobSpecialtyChangedEvent」、
 * spec/05-persistence.md 「player_job / player_job_history」、
 * spec/08-permissions.md 「管理系」および
 * docs/plan/class-structure.md 「specialty」を参照。
 *
 * <p>起動時にプレイヤーログインで player_job の 1 行をキャッシュに読み、
 * 以降は cache とリポジトリを同期して扱う。履歴は append-only なので
 * cache しない（PlayerJobHistoryRepository を都度参照する）。
 */
public final class SpecialtyService {

    private final Plugin plugin;
    private final PlayerJobRepository repository;
    private final PlayerJobHistoryRepository historyRepository;
    private final JobRegistry jobRegistry;
    private final CooldownPolicy cooldownPolicy;
    private final Clock clock;

    /** 現在の専業。null は未選択。 */
    private final ConcurrentHashMap<UUID, JobId> currentCache = new ConcurrentHashMap<>();
    /** cooldown 起点時刻。null はまだ選択履歴がない。 */
    private final ConcurrentHashMap<UUID, Instant> cooldownBaseCache = new ConcurrentHashMap<>();

    public SpecialtyService(
            Plugin plugin,
            PlayerJobRepository repository,
            PlayerJobHistoryRepository historyRepository,
            JobRegistry jobRegistry,
            CooldownPolicy cooldownPolicy
    ) {
        this(plugin, repository, historyRepository, jobRegistry, cooldownPolicy, Clock.systemUTC());
    }

    public SpecialtyService(
            Plugin plugin,
            PlayerJobRepository repository,
            PlayerJobHistoryRepository historyRepository,
            JobRegistry jobRegistry,
            CooldownPolicy cooldownPolicy,
            Clock clock
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.jobRegistry = jobRegistry;
        this.cooldownPolicy = cooldownPolicy;
        this.clock = clock;
    }

    /** プレイヤーログイン時に cache を warm する。 */
    public void loadPlayer(UUID player) {
        repository.find(player).ifPresentOrElse(
                row -> {
                    try {
                        currentCache.put(player, new JobId(row.jobId()));
                        cooldownBaseCache.put(player, row.cooldownBaseAt());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning(
                                "player_job for " + player + " has invalid job id: " + row.jobId()
                        );
                    }
                },
                () -> {
                    currentCache.remove(player);
                    cooldownBaseCache.remove(player);
                }
        );
    }

    public void unloadPlayer(UUID player) {
        currentCache.remove(player);
        cooldownBaseCache.remove(player);
    }

    /** cache から現在の専業を返す。cache miss は Optional.empty()。 */
    public Optional<JobId> currentJob(UUID player) {
        return Optional.ofNullable(currentCache.get(player));
    }

    /**
     * オフライン含む対象プレイヤーの現在専業を DB から直接引く。
     * {@code /jobs admin inspect} 経路で使う。
     */
    public Optional<PlayerJobRow> inspect(UUID player) {
        return repository.find(player);
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
        UUID uuid = player.getUniqueId();
        repository.upsert(uuid, jobId.value(), now);
        historyRepository.append(uuid, jobId.value(), null, now, Actor.PLAYER, null);
        currentCache.put(uuid, jobId);
        cooldownBaseCache.put(uuid, now);
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
        // BYPASS_COOLDOWN を持つプレイヤーはクールダウン判定をスキップする。
        // nextAvailableAt が返す値は cooldownBaseCache 通り (base + cooldown) のままなので、
        // /jobs status の表示は変わらない。
        if (!player.hasPermission(Permissions.BYPASS_COOLDOWN)) {
            Duration cooldown = cooldownPolicy.currentCooldown(now);
            Instant base = cooldownBaseCache.get(uuid);
            if (base != null && !cooldown.isZero()) {
                Instant nextAvailable = base.plus(cooldown);
                if (nextAvailable.isAfter(now)) {
                    return new SpecialtyChangeResult.CooldownRemaining(
                            Duration.between(now, nextAvailable), nextAvailable
                    );
                }
            }
        }
        repository.upsert(uuid, newJobId.value(), now);
        historyRepository.append(uuid, newJobId.value(), current.value(), now, Actor.PLAYER, null);
        currentCache.put(uuid, newJobId);
        cooldownBaseCache.put(uuid, now);
        fireEvent(player, current, newJobId, now);
        return new SpecialtyChangeResult.Success(current, newJobId, now, false);
    }

    /**
     * {@code /jobs admin set} 経路。cooldown 判定なしで対象の専業を強制付与する。
     * オンラインなら caches を同期更新して JobSpecialtyChangedEvent を発火する。
     * オフラインなら DB 側のみ更新（event はスキップ）。
     *
     * @param target 対象プレイヤーの UUID。オフラインでも可。
     * @param jobId 付与する専業。JobRegistry で validate 済みでない場合は UnknownJob を返す。
     * @param actorUuid 実行した管理者の UUID。監査ログに記録される。null 不可。
     */
    public SpecialtyChangeResult setForced(UUID target, JobId jobId, UUID actorUuid) {
        if (!jobRegistry.get(jobId).isPresent()) {
            return new SpecialtyChangeResult.UnknownJob(jobId);
        }
        Instant now = Instant.now(clock);
        String previousJobId = repository.find(target)
                .map(PlayerJobRow::jobId)
                .orElse(null);
        repository.upsert(target, jobId.value(), now);
        historyRepository.append(target, jobId.value(), previousJobId, now, Actor.ADMIN, actorUuid);

        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            JobId previousId = previousJobId == null ? null : new JobId(previousJobId);
            currentCache.put(target, jobId);
            cooldownBaseCache.put(target, now);
            fireEvent(online, previousId, jobId, now);
        }
        JobId previousId = previousJobId == null ? null : new JobId(previousJobId);
        return new SpecialtyChangeResult.Success(previousId, jobId, now, previousId == null);
    }

    /**
     * {@code /jobs admin reset-cooldown} 経路。cooldown 起点を EPOCH に上書きし、
     * 次回変更判定を即通過させる。専業自体は変更しない。履歴には残さない。
     */
    public void resetCooldown(UUID target) {
        repository.resetCooldownBase(target);
        // オンラインなら cache も同期。currentCache は触らない。
        if (cooldownBaseCache.containsKey(target)) {
            cooldownBaseCache.put(target, Instant.EPOCH);
        }
    }

    /** 次回変更可能時刻。cooldown 起点が cache に無い場合は Optional.empty()。 */
    public Optional<Instant> nextAvailableAt(UUID player) {
        Instant base = cooldownBaseCache.get(player);
        if (base == null) return Optional.empty();
        Duration cooldown = cooldownPolicy.currentCooldown(Instant.now(clock));
        return Optional.of(base.plus(cooldown));
    }

    /** 変更履歴を持たない（未選択）プレイヤーか。 */
    public boolean isFirstTime(UUID player) {
        return !currentCache.containsKey(player);
    }

    /** ログイン時に cache に読み込むための同期ヘルパ。 */
    public Optional<PlayerJobRow> loadRow(UUID player) {
        return repository.find(player);
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
