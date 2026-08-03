package me.f0reach.jobs.pipeline.async;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardWorkQueueTest {

    /** WARNING を捕まえるだけの Handler。 */
    private static final class Capture extends Handler {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(record.getMessage());
            }
        }

        @Override public void flush() {}
        @Override public void close() {}
    }

    private static Logger logger(Capture capture) {
        Logger logger = Logger.getLogger("RewardWorkQueueTest-" + System.identityHashCode(capture));
        logger.setUseParentHandlers(false);
        logger.addHandler(capture);
        return logger;
    }

    @Test
    void pollReturnsTasksInFifoOrder() throws Exception {
        Capture capture = new Capture();
        RewardWorkQueue q = new RewardWorkQueue(logger(capture), 10, List.of());
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int n = i;
            assertTrue(q.offerReward(() -> seen.add(n)));
        }
        for (int i = 0; i < 3; i++) {
            q.poll(50, TimeUnit.MILLISECONDS).run();
        }
        assertEquals(List.of(0, 1, 2), seen);
    }

    @Test
    void pollReturnsNullWhenEmpty() throws Exception {
        Capture capture = new Capture();
        RewardWorkQueue q = new RewardWorkQueue(logger(capture), 10, List.of());
        assertEquals(null, q.poll(10, TimeUnit.MILLISECONDS));
    }

    @Test
    void offerBeyondCapacityDropsAndCounts() {
        Capture capture = new Capture();
        RewardWorkQueue q = new RewardWorkQueue(logger(capture), 2, List.of());
        assertTrue(q.offerReward(() -> {}));
        assertTrue(q.offerReward(() -> {}));
        assertFalse(q.offerReward(() -> {}));
        assertFalse(q.offerReward(() -> {}));
        assertEquals(2, q.droppedTotal());
        assertEquals(2, q.size());
    }

    @Test
    void dropWarningIsRateLimitedAndReportsCountSinceLastReport() {
        Capture capture = new Capture();
        AtomicLong now = new AtomicLong(1L);
        RewardWorkQueue q = new RewardWorkQueue(logger(capture), 1, List.of(), now::get);
        assertTrue(q.offerReward(() -> {}));

        // 同一時刻の 3 件連続 drop は 1 回しか警告しない。
        q.offerReward(() -> {});
        q.offerReward(() -> {});
        q.offerReward(() -> {});
        assertEquals(1, capture.warnings.size());
        assertTrue(capture.warnings.get(0).contains("dropped 1 task(s)"));

        // 30 秒経過後は再び警告し、前回報告以降の件数を出す。
        now.set(1L + 31L * 1_000_000_000L);
        q.offerReward(() -> {});
        assertEquals(2, capture.warnings.size());
        assertTrue(capture.warnings.get(1).contains("dropped 3 task(s)"));
        assertEquals(4, q.droppedTotal());
    }

    @Test
    void backlogWarningFiresOncePerIntervalAtHighestCrossedThreshold() {
        Capture capture = new Capture();
        AtomicLong now = new AtomicLong(1L);
        RewardWorkQueue q = new RewardWorkQueue(logger(capture), 10, List.of(0.5, 0.8), now::get);

        for (int i = 0; i < 5; i++) q.offerReward(() -> {});
        assertEquals(1, capture.warnings.size());
        assertTrue(capture.warnings.get(0).contains("threshold 50%"));

        // 80% を超えても、30 秒経つまでは出さない。
        for (int i = 0; i < 4; i++) q.offerReward(() -> {});
        assertEquals(1, capture.warnings.size());

        now.set(1L + 31L * 1_000_000_000L);
        q.offerReward(() -> {});
        assertEquals(2, capture.warnings.size());
        assertTrue(capture.warnings.get(1).contains("threshold 80%"));
    }

    @Test
    void noBacklogWarningBelowLowestThreshold() {
        Capture capture = new Capture();
        RewardWorkQueue q = new RewardWorkQueue(logger(capture), 10, List.of(0.5, 0.8));
        for (int i = 0; i < 4; i++) q.offerReward(() -> {});
        assertTrue(capture.warnings.isEmpty());
    }

    @Test
    void drainAllEmptiesQueue() {
        Capture capture = new Capture();
        RewardWorkQueue q = new RewardWorkQueue(logger(capture), 10, List.of());
        q.offerReward(() -> {});
        q.offerReward(() -> {});
        assertEquals(2, q.drainAll().size());
        assertTrue(q.isEmpty());
    }
}
