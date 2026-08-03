package me.f0reach.jobs.pipeline.async;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardWorkerTest {

    private static Logger silentLogger() {
        Logger logger = Logger.getLogger("RewardWorkerTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    @Test
    void processesTasksInEnqueueOrder() throws Exception {
        Logger logger = silentLogger();
        RewardWorkQueue queue = new RewardWorkQueue(logger, 100, List.of());
        RewardWorker worker = new RewardWorker(logger, queue);
        List<Integer> seen = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(50);

        worker.start();
        try {
            for (int i = 0; i < 50; i++) {
                int n = i;
                queue.offerReward(() -> {
                    seen.add(n);
                    done.countDown();
                });
            }
            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            worker.drainAndStop(1_000);
        }

        assertEquals(50, seen.size());
        for (int i = 0; i < 50; i++) {
            assertEquals(i, seen.get(i));
        }
    }

    @Test
    void taskFailureDoesNotStopTheWorker() throws Exception {
        Logger logger = silentLogger();
        RewardWorkQueue queue = new RewardWorkQueue(logger, 100, List.of());
        RewardWorker worker = new RewardWorker(logger, queue);
        CountDownLatch after = new CountDownLatch(1);

        worker.start();
        try {
            queue.offerReward(() -> { throw new IllegalStateException("boom"); });
            queue.offerReward(after::countDown);
            assertTrue(after.await(5, TimeUnit.SECONDS));
        } finally {
            worker.drainAndStop(1_000);
        }
        assertEquals(2, worker.processedTotal());
    }

    @Test
    void drainAndStopFinishesQueuedTasks() throws Exception {
        Logger logger = silentLogger();
        RewardWorkQueue queue = new RewardWorkQueue(logger, 1_000, List.of());
        RewardWorker worker = new RewardWorker(logger, queue);
        List<Integer> seen = new CopyOnWriteArrayList<>();

        // 起動前に積んでおく。停止要求後もキューを空にしてから抜けることを確認する。
        for (int i = 0; i < 200; i++) {
            int n = i;
            queue.offerReward(() -> seen.add(n));
        }
        worker.start();
        worker.drainAndStop(5_000);

        assertTrue(queue.isEmpty());
        assertEquals(200, seen.size());
        assertEquals(200, worker.processedTotal());
    }

    @Test
    void drainAndStopWarnsWhenTasksRemainAfterTimeout() throws Exception {
        Logger logger = silentLogger();
        RewardWorkQueue queue = new RewardWorkQueue(logger, 100, List.of());
        RewardWorker worker = new RewardWorker(logger, queue);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        worker.start();
        try {
            queue.offerReward(() -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            queue.offerReward(() -> {});
            assertTrue(started.await(5, TimeUnit.SECONDS));

            // 先頭タスクが握ったままなので、短い timeout では終わらない。
            worker.drainAndStop(100);
            assertEquals(1, queue.size());
        } finally {
            release.countDown();
        }
    }
}
