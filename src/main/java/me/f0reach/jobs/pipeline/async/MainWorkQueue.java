package me.f0reach.jobs.pipeline.async;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ワーカースレッドから main thread へ渡す副作用の受け皿。
 *
 * <p>docs/plan/async-reward-pipeline.md 「main thread への投げ返し」を参照。
 * 送金 1 件ごとに {@code runTask} を積むと、スケジュールしたタスクが次の tick で
 * まとめて実行されるため、キューに数万件が溜まった状態では 1 tick に数万件が走る。
 * ここに積んで毎 tick {@code perTick} 件までに絞ることで、キュー深度に関わらず
 * 1 tick の滞在時間を一定に保つ。
 *
 * <p>{@link #drainTick()} を毎 tick 呼ぶ scheduler の所有は呼び出し側（{@code JobsServices}）に置く。
 */
public final class MainWorkQueue {

    private final Logger logger;
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    /** ConcurrentLinkedQueue#size は O(n) なので別に数える。 */
    private final AtomicInteger pending = new AtomicInteger();
    private final int perTick;

    public MainWorkQueue(Logger logger, int perTick) {
        if (perTick <= 0) throw new IllegalArgumentException("perTick must be > 0");
        this.logger = logger;
        this.perTick = perTick;
    }

    /** ワーカースレッドから呼ぶ。main thread の完了は待たない。 */
    public void post(Runnable task) {
        queue.add(task);
        pending.incrementAndGet();
    }

    public int pending() { return pending.get(); }

    public int perTick() { return perTick; }

    /**
     * main thread から毎 tick 呼ぶ。最大 {@code perTick} 件を実行する。
     *
     * @return 実行した件数
     */
    public int drainTick() {
        int count = 0;
        while (count < perTick) {
            Runnable task = poll();
            if (task == null) break;
            execute(task);
            count++;
        }
        return count;
    }

    /**
     * 停止時に残り全件をその場で実行する。
     *
     * <p>{@code onDisable} は main thread で走るので、tick ドレイナが止まっていても
     * ここで直接空にすれば送金は正しいスレッドで完了する。
     * 停止処理なので tick あたりの上限は掛けない。
     *
     * @return 実行した件数
     */
    public int drainAllInline() {
        int count = 0;
        Runnable task;
        while ((task = poll()) != null) {
            execute(task);
            count++;
        }
        return count;
    }

    private Runnable poll() {
        Runnable task = queue.poll();
        if (task != null) pending.decrementAndGet();
        return task;
    }

    private void execute(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | Error e) {
            logger.log(Level.SEVERE, "main-thread reward task threw", e);
        }
    }
}
