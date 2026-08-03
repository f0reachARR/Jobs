package me.f0reach.jobs.pipeline.async;

import me.f0reach.jobs.util.AsyncExecutor;

/**
 * {@code reward.async.enabled: false} のときの {@link RewardDispatcher}。
 * 全段階と制御タスクを main thread で走らせる（非同期化前の挙動）。
 *
 * <p>{@link AsyncExecutor#runOnMain(Runnable)} は既に main thread なら
 * その場で実行するため、listener からの報酬処理は同期に完了する。
 */
public final class MainThreadRewardDispatcher implements RewardDispatcher {

    private final AsyncExecutor asyncExecutor;

    public MainThreadRewardDispatcher(AsyncExecutor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public boolean dispatchReward(Runnable task) {
        asyncExecutor.runOnMain(task);
        return true;
    }

    @Override
    public void dispatchControl(Runnable task) {
        asyncExecutor.runOnMain(task);
    }
}
