package me.f0reach.jobs.persistence;

import me.f0reach.jobs.persistence.dto.Actor;
import me.f0reach.jobs.persistence.dto.PlayerJobHistoryRow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * player_job_history テーブルへのアクセス interface。
 * spec/05-persistence.md 「player_job_history」に対応。
 *
 * <p>append-only の監査ログ。SpecialtyService#select / change / setForced から
 * 呼ばれ、成功時のみ 1 行 append される。reset-cooldown は履歴を残さない。
 */
public interface PlayerJobHistoryRepository {

    /**
     * 履歴を 1 行追加する。
     * @param previousJobId 初回選択時は null。
     * @param actorUuid actor=ADMIN のとき管理者 UUID、それ以外は null 可。
     */
    void append(UUID player,
                String jobId,
                String previousJobId,
                Instant changedAt,
                Actor actor,
                UUID actorUuid);

    /** 直近 {@code limit} 件を changed_at 降順で返す。管理系 inspect / audit 用。 */
    List<PlayerJobHistoryRow> recent(UUID player, int limit);

    /**
     * 最古の履歴 row の changed_at を返す。Quest プラグインの
     * {@code since: quest_start} 判定などで使う想定。行が無ければ empty。
     */
    Optional<Instant> firstSelectedAt(UUID player);
}
