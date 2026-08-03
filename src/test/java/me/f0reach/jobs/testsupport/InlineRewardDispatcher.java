package me.f0reach.jobs.testsupport;

import me.f0reach.jobs.pipeline.async.RewardDispatcher;

/**
 * テスト用の {@link RewardDispatcher}。投入されたタスクを呼び出しスレッドで即実行する。
 * ワーカーを跨がずに決定的に検証したい単体テストで使う。
 */
public final class InlineRewardDispatcher implements RewardDispatcher {

    @Override
    public boolean dispatchReward(Runnable task) {
        task.run();
        return true;
    }

    @Override
    public void dispatchControl(Runnable task) {
        task.run();
    }
}
