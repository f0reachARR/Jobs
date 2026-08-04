package me.f0reach.jobs.modifier;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * 拡張 Modifier / Splitter 1 件の所要時間を測り、閾値を超えたら WARNING を出す。
 *
 * <p>ワーカーは 1 本しかないので、遅い拡張実装はサーバ全体の報酬処理を詰まらせる
 * （docs/plan/async-reward-pipeline.md 「拡張 API の契約変更」）。
 * どの {@code getId()} が遅いかを運用者が特定できるようにする。
 *
 * <p>同じ実装が毎アクションで遅い場合にログを埋めないよう、id ごとではなく
 * 全体で 30 秒に 1 回へ絞る。
 */
public final class SlowExtensionReporter {

    private static final long WARN_INTERVAL_NANOS = 30L * 1_000_000_000L;

    private final Logger logger;
    private final LongSupplier thresholdMs;
    private final LongSupplier nanoTime;
    private final AtomicLong lastWarnAt = new AtomicLong();

    public static SlowExtensionReporter disabled() {
        return new SlowExtensionReporter(Logger.getLogger(SlowExtensionReporter.class.getName()), 0L);
    }

    public SlowExtensionReporter(Logger logger, long thresholdMs) {
        this(logger, () -> thresholdMs, System::nanoTime);
    }

    /**
     * config を参照して組む。{@code /jobs reload} で閾値が差し替わるので、
     * 計測のたびに supplier から読み直す。
     */
    public SlowExtensionReporter(Logger logger, LongSupplier thresholdMs) {
        this(logger, thresholdMs, System::nanoTime);
    }

    SlowExtensionReporter(Logger logger, long thresholdMs, LongSupplier nanoTime) {
        this(logger, () -> thresholdMs, nanoTime);
    }

    private SlowExtensionReporter(Logger logger, LongSupplier thresholdMs, LongSupplier nanoTime) {
        this.logger = logger;
        this.thresholdMs = thresholdMs;
        this.nanoTime = nanoTime;
    }

    public boolean enabled() { return thresholdNanos() > 0L; }

    private long thresholdNanos() {
        long ms = thresholdMs.getAsLong();
        return ms <= 0 ? 0L : ms * 1_000_000L;
    }

    /** 計測開始時刻。無効時は 0 を返し、計測そのものを省く。 */
    public long now() {
        return enabled() ? nanoTime.getAsLong() : 0L;
    }

    /** {@link #now()} の戻り値を渡す。閾値超過なら WARNING を出す。 */
    public void reportIfSlow(String kind, String id, long startedAt) {
        long threshold = thresholdNanos();
        if (threshold <= 0L) return;
        long elapsed = nanoTime.getAsLong() - startedAt;
        if (elapsed < threshold) return;
        if (!shouldWarn()) return;
        logger.warning(String.format(
                "%s '%s' took %.1fms on the reward worker;"
                        + " blocking work here stalls reward processing for every player",
                kind, id, elapsed / 1_000_000.0
        ));
    }

    private boolean shouldWarn() {
        long now = nanoTime.getAsLong();
        while (true) {
            long last = lastWarnAt.get();
            boolean first = last == 0L;
            if (!first && now - last < WARN_INTERVAL_NANOS) return false;
            long stamp = now == 0L ? 1L : now;
            if (lastWarnAt.compareAndSet(last, stamp)) return true;
        }
    }
}
