package me.f0reach.jobs.persistence;

import me.f0reach.jobs.api.query.ActionFilter;
import me.f0reach.jobs.api.query.ActionLogQueryService;
import me.f0reach.jobs.api.query.TimeRange;
import me.f0reach.jobs.util.AsyncExecutor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ActionLogQueryService} の実装。
 * spec/06-public-api.md 「ActionLogQueryService」および class-structure.md 「persistence」を参照。
 *
 * <p>{@link ActionLogRepository} を素直に叩き、{@link AsyncExecutor} で
 * 非同期実行する。呼び出しは main thread からでも安全 (thread は AsyncExecutor が管理)。
 */
public final class ActionLogQueryServiceImpl implements ActionLogQueryService {

    private final ActionLogRepository repository;
    private final AsyncExecutor asyncExecutor;

    public ActionLogQueryServiceImpl(ActionLogRepository repository, AsyncExecutor asyncExecutor) {
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public CompletableFuture<Long> countActions(UUID player, ActionFilter filter, TimeRange range) {
        return asyncExecutor.supplyAsync(() -> repository.countActions(player, filter, range));
    }

    @Override
    public CompletableFuture<Double> sumReward(UUID player, ActionFilter filter, TimeRange range) {
        return asyncExecutor.supplyAsync(() -> repository.sumReward(player, filter, range));
    }

    @Override
    public CompletableFuture<Set<String>> distinctKeys(UUID player, ActionFilter filter, TimeRange range) {
        return asyncExecutor.supplyAsync(() -> repository.distinctKeys(player, filter, range));
    }

    @Override
    public CompletableFuture<Integer> continuousStreakSec(UUID player, ActionFilter filter, TimeRange range) {
        return asyncExecutor.supplyAsync(() -> repository.continuousStreakSec(player, filter, range));
    }

    @Override
    public CompletableFuture<Double> maxUnitPrice(UUID player, ActionFilter filter, TimeRange range) {
        return asyncExecutor.supplyAsync(() -> repository.maxUnitPrice(player, filter, range));
    }
}
