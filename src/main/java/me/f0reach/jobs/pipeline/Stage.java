package me.f0reach.jobs.pipeline;

/**
 * パイプラインの 1 段階。
 *
 * <p>実行スレッドは {@link #affinity()} で宣言する。docs/plan/async-reward-pipeline.md を参照。
 * {@link Affinity#MAIN} の段階は listener の呼び出しスレッド（main thread）で同期実行され、
 * {@link Affinity#WORKER} の段階は {@code RewardWorker} 上で走る。
 */
public interface Stage {

    /** Stage 実行結果。CONTINUE で次段階へ、HALT で以降を打ち切って処理終了。 */
    enum Result { CONTINUE, HALT }

    /**
     * Stage を走らせるスレッド。
     *
     * <p>{@link #MAIN} は Bukkit の状態を触る段階に使う。prologue としてまとめて
     * listener の中で実行されるので、同 tick 内でしか有効でない {@code Block} や
     * {@code Entity} 参照を安全に読める。
     *
     * <p>{@link #WORKER} は Bukkit API を直接叩かない段階に使う。Bukkit を要する副作用は
     * {@code MainWorkQueue} か {@code AsyncExecutor#runOnMain} へ投げ返す。
     */
    enum Affinity { MAIN, WORKER }

    Result execute(PipelineContext ctx);

    /** この Stage を走らせるスレッド。既定は MAIN。 */
    default Affinity affinity() { return Affinity.MAIN; }
}
