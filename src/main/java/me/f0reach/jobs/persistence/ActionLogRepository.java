package me.f0reach.jobs.persistence;

import me.f0reach.jobs.api.query.ActionFilter;
import me.f0reach.jobs.api.query.TimeRange;
import me.f0reach.jobs.persistence.dto.ActionLogRow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * action_log テーブルへのアクセス interface。
 * spec/05-persistence.md 「action_log」および spec/06-public-api.md 「ActionLogQueryService」を参照。
 *
 * insertBatch は BatchFlushWorker から呼ばれ、集計系は AsyncExecutor 経由で呼ばれる（threading.md）。
 */
public interface ActionLogRepository {

    void insertBatch(List<ActionLogRow> rows);

    long countActions(UUID player, ActionFilter filter, TimeRange range);

    double sumReward(UUID player, ActionFilter filter, TimeRange range);

    Set<String> distinctKeys(UUID player, ActionFilter filter, TimeRange range);

    int continuousStreakSec(UUID player, ActionFilter filter, TimeRange range);

    double maxUnitPrice(UUID player, ActionFilter filter, TimeRange range);

    int deleteOlderThan(Instant cutoff);

    /** Phase 6 の VarietyRingBuffer 初期化で使う。直近 limit 件の action_key を新しい順に返す。 */
    List<String> recentKeys(UUID player, String jobId, int limit);

    /** Phase 6 の DailyTotalCache 初期化で使う。range 内の final_reward を job_id ごとに合算して返す。 */
    Map<String, Double> sumRewardByJob(UUID player, TimeRange range);
}
