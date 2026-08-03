package me.f0reach.jobs.pipeline.async;

/**
 * 報酬処理と、それに付随する可変状態の更新をどのスレッドで走らせるかを決める口。
 *
 * <p>docs/plan/async-reward-pipeline.md を参照。
 * {@code reward.async.enabled} が true なら {@link WorkerRewardDispatcher} が
 * 単一ワーカースレッドへ、false なら {@link MainThreadRewardDispatcher} が
 * main thread へ流す。どちらの実装でも「可変状態の書き手は 1 スレッド」が保たれる。
 */
public interface RewardDispatcher {

    /**
     * 報酬処理（段階 4 以降）を投入する。
     *
     * @return 受け付けたら true。キュー溢れで捨てたら false。
     */
    boolean dispatchReward(Runnable task);

    /**
     * cache の warmup 反映 / unload / reset といった制御タスクを投入する。
     *
     * <p>報酬タスクと同じ順序で処理される。{@code unload} が先回りして
     * 「キューに残っている同プレイヤーの報酬処理が cache を作り直す」事故を防ぐため、
     * 優先実行はしない。溢れても捨てない。
     */
    void dispatchControl(Runnable task);
}
