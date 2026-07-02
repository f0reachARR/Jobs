package me.f0reach.jobs.api.query;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 行動ログの集計クエリの公開 API。
 * spec/06-public-api.md 「ActionLogQueryService」を参照。
 *
 * <p>すべて {@link CompletableFuture} で返す。呼び出しは main thread から行ってよい
 * (実装が非同期スレッドプールで DB クエリを発行する)。
 */
public interface ActionLogQueryService {

    CompletableFuture<Long> countActions(UUID player, ActionFilter filter, TimeRange range);

    CompletableFuture<Double> sumReward(UUID player, ActionFilter filter, TimeRange range);

    CompletableFuture<Set<String>> distinctKeys(UUID player, ActionFilter filter, TimeRange range);

    CompletableFuture<Integer> continuousStreakSec(UUID player, ActionFilter filter, TimeRange range);

    CompletableFuture<Double> maxUnitPrice(UUID player, ActionFilter filter, TimeRange range);
}
