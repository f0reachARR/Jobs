package me.f0reach.jobs.specialty;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit の playerdata が持つ初参加時刻 ({@code getFirstPlayed}) を使う実装。
 *
 * <p>オンラインのプレイヤーはログイン時に温めたキャッシュから引く。オフラインの
 * UUID を渡された場合のみ {@link Bukkit#getOfflinePlayer(UUID)} 経由で playerdata を
 * 読むため、ディスク I/O が走る。この経路を通るのは、オフライン相手の
 * {@code /jobs admin inspect} など呼び出し頻度が低いものに限る。
 *
 * <p>制約は ADR-0022 を参照。playerdata 依存なので、複数バックエンド構成では
 * サーバーごとの値になり、playerdata を消すと初参加時刻もリセットされる。
 */
public final class BukkitFirstJoinProvider implements FirstJoinProvider {

    private final ConcurrentHashMap<UUID, Instant> cache = new ConcurrentHashMap<>();
    private final Clock clock;

    public BukkitFirstJoinProvider() {
        this(Clock.systemUTC());
    }

    public BukkitFirstJoinProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<Instant> firstJoinAt(UUID player) {
        Instant cached = cache.get(player);
        if (cached != null) {
            return Optional.of(cached);
        }
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            Instant resolved = resolveOnline(online);
            cache.put(player, resolved);
            return Optional.of(resolved);
        }
        long raw = Bukkit.getOfflinePlayer(player).getFirstPlayed();
        return raw <= 0L ? Optional.empty() : Optional.of(Instant.ofEpochMilli(raw));
    }

    @Override
    public void warm(Player player) {
        cache.put(player.getUniqueId(), resolveOnline(player));
    }

    @Override
    public void evict(UUID player) {
        cache.remove(player);
    }

    /**
     * オンライン経路で値が取れないときは「今まさに初参加した」とみなす。
     * 新規プレイヤーが playerdata の書き込み前に判定されて、初参加優遇ではなく
     * default 側の長い cooldown に落ちるのを防ぐ。
     */
    private Instant resolveOnline(Player player) {
        long raw = player.getFirstPlayed();
        return raw <= 0L ? Instant.now(clock) : Instant.ofEpochMilli(raw);
    }
}
