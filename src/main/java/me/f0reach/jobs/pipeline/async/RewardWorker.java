package me.f0reach.jobs.pipeline.async;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 報酬パイプラインの段階 4 以降を回す単一 daemon スレッド。
 *
 * <p>docs/plan/async-reward-pipeline.md 「実行モデル」を参照。
 * ワーカーを 1 本に絞ることで、{@code DailyTotalCache} と {@code VarietyRingBuffer} の
 * read-modify-write が自動的に直列化される。プレイヤー単位で実行順序を揃える仕組みは要らない。
 *
 * <p>main thread の完了を待たない。Bukkit API を要する副作用は
 * {@link MainWorkQueue} か {@code AsyncExecutor#runOnMain} へ投げ返して次のタスクへ進む。
 */
public final class RewardWorker implements Runnable {

    /** キューが空のときの待ち時間。停止要求の反映もこの周期で見る。 */
    private static final long POLL_INTERVAL_MS = 200;
    /** 処理レートを再計算する間隔。 */
    private static final long RATE_SAMPLE_NANOS = 1_000_000_000L;
    /** 処理レートの平滑化係数。 */
    private static final double RATE_ALPHA = 0.3;

    private final Logger logger;
    private final RewardWorkQueue queue;
    private final LongSupplier nanoTime;

    private final AtomicLong processedTotal = new AtomicLong();

    private volatile boolean running = true;
    private volatile double ratePerSecond;
    private Thread thread;

    private long rateSampledAt;
    private long processedAtSample;

    public RewardWorker(Logger logger, RewardWorkQueue queue) {
        this(logger, queue, System::nanoTime);
    }

    RewardWorker(Logger logger, RewardWorkQueue queue, LongSupplier nanoTime) {
        this.logger = logger;
        this.queue = queue;
        this.nanoTime = nanoTime;
    }

    public void start() {
        rateSampledAt = nanoTime.getAsLong();
        thread = new Thread(this, "Jobs-Reward");
        thread.setDaemon(true);
        thread.start();
    }

    /** 起動以降に処理したタスクの累計。 */
    public long processedTotal() { return processedTotal.get(); }

    /** 直近の処理レート（件 / 秒）。EWMA で平滑化した値。 */
    public double ratePerSecond() { return ratePerSecond; }

    /**
     * 停止要求を出し、残キューを処理し切るまで {@code timeoutMs} 待つ。
     *
     * <p>drain はワーカースレッド自身が行う。呼び出し元（main thread）でインライン実行すると
     * 可変状態の書き手が 2 本になり、単一ワーカーの前提が崩れる。
     * タイムアウトした場合は未処理件数を WARNING に出す。
     */
    public void drainAndStop(long timeoutMs) {
        running = false;
        if (thread == null) return;
        try {
            thread.join(Math.max(0, timeoutMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            logger.warning(
                    "reward worker did not finish within " + timeoutMs + "ms; "
                            + queue.size() + " task(s) left unprocessed"
            );
        }
        thread = null;
    }

    @Override
    public void run() {
        // 停止要求後も、積まれているタスクは処理し切ってから抜ける。
        while (running || !queue.isEmpty()) {
            Runnable task;
            try {
                task = queue.poll(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (task == null) {
                sampleRate();
                continue;
            }
            try {
                task.run();
            } catch (RuntimeException | Error e) {
                // 1 件の失敗でワーカーを落とさない。
                logger.log(Level.SEVERE, "reward worker task threw", e);
            }
            processedTotal.incrementAndGet();
            sampleRate();
        }
    }

    /** 1 秒ごとに処理レートを再計算する。 */
    private void sampleRate() {
        long now = nanoTime.getAsLong();
        long elapsed = now - rateSampledAt;
        if (elapsed < RATE_SAMPLE_NANOS) return;
        long processed = processedTotal.get();
        double observed = (processed - processedAtSample) * 1_000_000_000.0 / elapsed;
        ratePerSecond = ratePerSecond * (1.0 - RATE_ALPHA) + observed * RATE_ALPHA;
        rateSampledAt = now;
        processedAtSample = processed;
    }
}
