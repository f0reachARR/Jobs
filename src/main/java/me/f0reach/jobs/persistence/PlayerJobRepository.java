package me.f0reach.jobs.persistence;

import me.f0reach.jobs.persistence.dto.PlayerJobRow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * player_job テーブルへのアクセス interface。
 * spec/05-persistence.md 「player_job」に対応。
 *
 * <p>1 player = 1 row の現在専業テーブル。変更履歴は
 * {@link PlayerJobHistoryRepository} 側で扱う。
 *
 * <p>すべての呼び出しは Bukkit main thread から可（JDBC のネットワーク I/O は数回程度の呼び出しなら
 * 許容範囲だが、負荷が問題になれば AsyncExecutor 経由に切り替える余地を残す。threading.md 参照）。
 */
public interface PlayerJobRepository {

    /** 現在の専業を返す。未選択なら Optional.empty()。 */
    Optional<PlayerJobRow> find(UUID player);

    /**
     * 現在専業を upsert する。1 行しかないため INSERT ... ON DUPLICATE KEY UPDATE 相当。
     * @param cooldownBaseAt 通常は now。cooldown 起点になる。
     */
    void upsert(UUID player, String jobId, Instant cooldownBaseAt);

    /**
     * cooldown 起点を {@link Instant#EPOCH} に上書きする。専業自体は変更しない。
     * {@code /jobs admin reset-cooldown} 経路で使う。
     */
    void resetCooldownBase(UUID player);

    /** 行を削除する。将来の /jobs admin unset 用。存在しない場合は no-op。 */
    void delete(UUID player);

    /**
     * 職業 id ごとの現在専業プレイヤー数を返す。/jobs admin stats で使う。
     * 全プレイヤー分をスキャンするため呼び出し頻度は稀な想定。
     */
    java.util.Map<String, Long> countByJob();
}
