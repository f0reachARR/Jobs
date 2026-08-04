package me.f0reach.jobs.specialty;

import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * プレイヤーがこのサーバーへ初参加した時刻を返す。
 *
 * <p>{@link CooldownPolicy} の {@code within.first_join_within} 判定で使う。
 * 値が取れない場合は {@link Optional#empty()} を返し、条件はマッチしない扱いになる
 * （＝より長い default 側の cooldown に落ちる）。
 *
 * <p>実装を差し替えられるようにしてあるのは、将来 playerdata ではなく自前 DB に
 * 初参加時刻を持たせる選択肢を残すためと、テストを Bukkit 非依存に保つため
 * （ADR-0022）。
 */
public interface FirstJoinProvider {

    Optional<Instant> firstJoinAt(UUID player);

    /** ログイン時にキャッシュを温める。キャッシュを持たない実装では何もしない。 */
    default void warm(Player player) {
    }

    /** ログアウト時にキャッシュを捨てる。 */
    default void evict(UUID player) {
    }
}
