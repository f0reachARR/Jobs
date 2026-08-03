package me.f0reach.jobs.pipeline.async;

/**
 * {@code reward.async.enabled: true} のときの {@link RewardDispatcher}。
 * 段階 4 以降と制御タスクを単一ワーカースレッドへ流す。
 */
public final class WorkerRewardDispatcher implements RewardDispatcher {

    private final RewardWorkQueue queue;

    public WorkerRewardDispatcher(RewardWorkQueue queue) {
        this.queue = queue;
    }

    @Override
    public boolean dispatchReward(Runnable task) {
        return queue.offerReward(task);
    }

    @Override
    public void dispatchControl(Runnable task) {
        queue.offerControl(task);
    }
}
