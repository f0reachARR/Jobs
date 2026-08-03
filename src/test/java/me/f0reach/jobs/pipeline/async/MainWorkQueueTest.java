package me.f0reach.jobs.pipeline.async;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainWorkQueueTest {

    private static Logger silentLogger() {
        Logger logger = Logger.getLogger("MainWorkQueueTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    @Test
    void drainTickRunsAtMostPerTickAndCarriesTheRestOver() {
        MainWorkQueue q = new MainWorkQueue(silentLogger(), 3);
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            int n = i;
            q.post(() -> seen.add(n));
        }
        assertEquals(7, q.pending());

        assertEquals(3, q.drainTick());
        assertEquals(List.of(0, 1, 2), seen);
        assertEquals(4, q.pending());

        assertEquals(3, q.drainTick());
        assertEquals(1, q.pending());

        assertEquals(1, q.drainTick());
        assertEquals(0, q.pending());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), seen);

        assertEquals(0, q.drainTick());
    }

    @Test
    void drainAllInlineIgnoresThePerTickLimit() {
        MainWorkQueue q = new MainWorkQueue(silentLogger(), 2);
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int n = i;
            q.post(() -> seen.add(n));
        }
        assertEquals(10, q.drainAllInline());
        assertEquals(10, seen.size());
        assertEquals(0, q.pending());
    }

    @Test
    void taskFailureDoesNotAbortTheDrain() {
        MainWorkQueue q = new MainWorkQueue(silentLogger(), 10);
        List<Integer> seen = new ArrayList<>();
        q.post(() -> { throw new IllegalStateException("boom"); });
        q.post(() -> seen.add(1));
        assertEquals(2, q.drainTick());
        assertEquals(List.of(1), seen);
        assertEquals(0, q.pending());
    }

    @Test
    void pendingCountStaysAccurate() {
        MainWorkQueue q = new MainWorkQueue(silentLogger(), 100);
        assertEquals(0, q.pending());
        q.post(() -> {});
        q.post(() -> {});
        assertEquals(2, q.pending());
        q.drainAllInline();
        assertTrue(q.pending() == 0);
    }
}
