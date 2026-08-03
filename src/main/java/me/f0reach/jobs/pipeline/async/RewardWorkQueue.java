package me.f0reach.jobs.pipeline.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * 報酬パイプラインの段階 4 以降を {@link RewardWorker} へ渡す境界キュー。
 *
 * <p>docs/plan/async-reward-pipeline.md 「境界キューの容量」を参照。
 * 容量分の配列を先に確保しないよう {@link LinkedBlockingQueue} を使う。
 * 平常時のキュー深度はほぼ 0 なので、容量を大きく取ってもメモリを占めない。
 *
 * <p>キューはタスクの直和を運ぶ。報酬タスクは段階 4 から 11 を走らせ、
 * 制御タスクは cache の warmup 反映 / unload / reset を走らせる。
 * どちらも {@link Runnable} として同じ順序で処理され、可変状態の書き手が 1 スレッドに保たれる。
 */
public final class RewardWorkQueue {

    /** 水位警告と drop 警告を出す最短間隔。同じ水位で出し続けないよう絞る。 */
    private static final long WARN_INTERVAL_NANOS = 30L * 1_000_000_000L;

    private final Logger logger;
    private final BlockingQueue<Runnable> queue;
    private final int capacity;
    private final double[] warnRatios;
    private final LongSupplier nanoTime;

    private final AtomicLong droppedTotal = new AtomicLong();

    /** 直近の水位警告時刻。0 は未警告。 */
    private final AtomicLong lastBacklogWarnAt = new AtomicLong();
    /** 直近の drop 警告時刻と、その時点での drop 累計。 */
    private final AtomicLong lastDropWarnAt = new AtomicLong();
    private final AtomicLong droppedAtLastWarn = new AtomicLong();

    public RewardWorkQueue(Logger logger, int capacity, List<Double> warnRatios) {
        this(logger, capacity, warnRatios, System::nanoTime);
    }

    RewardWorkQueue(Logger logger, int capacity, List<Double> warnRatios, LongSupplier nanoTime) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.logger = logger;
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.nanoTime = nanoTime;
        // 高い水位から順に見たいので降順に並べる。
        this.warnRatios = warnRatios.stream()
                .filter(r -> r != null && r > 0.0 && r <= 1.0)
                .mapToDouble(Double::doubleValue)
                .sorted()
                .toArray();
        reverse(this.warnRatios);
    }

    /**
     * タスクを積む。呼び出し側（main thread）は待たない。
     * 容量超過なら false を返し、drop を計上する。
     */
    public boolean offer(Runnable task) {
        if (!queue.offer(task)) {
            droppedTotal.incrementAndGet();
            warnDropped();
            return false;
        }
        warnBacklog(queue.size());
        return true;
    }

    /**
     * 1 件取り出す。{@code timeout} 経過までに来なければ null を返す。
     */
    public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /** 停止時に残りを取り出すヘルパ。 */
    public List<Runnable> drainAll() {
        List<Runnable> out = new ArrayList<>();
        queue.drainTo(out);
        return out;
    }

    public boolean isEmpty() { return queue.isEmpty(); }

    public int size() { return queue.size(); }

    public int capacity() { return capacity; }

    /** 起動以降に捨てたタスクの累計。{@code /jobs admin} で読む。 */
    public long droppedTotal() { return droppedTotal.get(); }

    /**
     * 水位が閾値を超えていれば WARNING を出す。
     * 到着レートがワーカーのスループットを恒常的に上回る状態を、drop が起きる前に見せる。
     */
    private void warnBacklog(int depth) {
        if (warnRatios.length == 0) return;
        double ratio = (double) depth / (double) capacity;
        double crossed = -1.0;
        for (double threshold : warnRatios) {
            if (ratio >= threshold) {
                crossed = threshold;
                break;
            }
        }
        if (crossed < 0.0) return;
        if (!shouldWarn(lastBacklogWarnAt)) return;
        logger.warning(String.format(
                "reward queue backlog %d/%d (%.0f%%, threshold %.0f%%);"
                        + " the worker is not keeping up with incoming actions",
                depth, capacity, ratio * 100.0, crossed * 100.0
        ));
    }

    private void warnDropped() {
        if (!shouldWarn(lastDropWarnAt)) return;
        long total = droppedTotal.get();
        long since = total - droppedAtLastWarn.getAndSet(total);
        logger.warning(String.format(
                "reward queue full (%d): dropped %d task(s) since the last report, %d total",
                capacity, since, total
        ));
    }

    /** 前回警告から {@link #WARN_INTERVAL_NANOS} 経過していれば true にして時刻を進める。 */
    private boolean shouldWarn(AtomicLong lastAt) {
        long now = nanoTime.getAsLong();
        while (true) {
            long last = lastAt.get();
            // 初回は last=0 だが、nanoTime は負値もありうるので経過判定ではなく別扱いにする。
            boolean first = last == 0L;
            if (!first && now - last < WARN_INTERVAL_NANOS) return false;
            long stamp = now == 0L ? 1L : now;
            if (lastAt.compareAndSet(last, stamp)) return true;
        }
    }

    private static void reverse(double[] values) {
        for (int i = 0, j = values.length - 1; i < j; i++, j--) {
            double tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
    }
}
