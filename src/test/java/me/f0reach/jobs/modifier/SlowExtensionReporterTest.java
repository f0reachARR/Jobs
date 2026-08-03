package me.f0reach.jobs.modifier;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlowExtensionReporterTest {

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
        Logger logger = Logger.getLogger("SlowExtensionReporterTest-" + System.identityHashCode(capture));
        logger.setUseParentHandlers(false);
        logger.addHandler(capture);
        return logger;
    }

    private static long ms(long value) { return value * 1_000_000L; }

    @Test
    void fastCallsAreNotReported() {
        Capture capture = new Capture();
        AtomicLong clock = new AtomicLong(ms(1));
        SlowExtensionReporter r = new SlowExtensionReporter(logger(capture), 50, clock::get);

        long start = r.now();
        clock.addAndGet(ms(10));
        r.reportIfSlow("JobRewardModifier", "fast", start);

        assertTrue(capture.warnings.isEmpty());
    }

    @Test
    void slowCallIsReportedWithTheOffendingId() {
        Capture capture = new Capture();
        AtomicLong clock = new AtomicLong(ms(1));
        SlowExtensionReporter r = new SlowExtensionReporter(logger(capture), 50, clock::get);

        long start = r.now();
        clock.addAndGet(ms(120));
        r.reportIfSlow("JobRewardModifier", "sluggish-bonus", start);

        assertEquals(1, capture.warnings.size());
        assertTrue(capture.warnings.get(0).contains("sluggish-bonus"));
        assertTrue(capture.warnings.get(0).contains("120.0ms"));
    }

    @Test
    void repeatedSlowCallsAreRateLimitedToOncePerInterval() {
        Capture capture = new Capture();
        AtomicLong clock = new AtomicLong(ms(1));
        SlowExtensionReporter r = new SlowExtensionReporter(logger(capture), 50, clock::get);

        for (int i = 0; i < 5; i++) {
            long start = r.now();
            clock.addAndGet(ms(100));
            r.reportIfSlow("JobRewardModifier", "sluggish", start);
        }
        assertEquals(1, capture.warnings.size());

        // 30 秒経過後は再び 1 回だけ出す。
        clock.addAndGet(31L * 1_000_000_000L);
        long start = r.now();
        clock.addAndGet(ms(100));
        r.reportIfSlow("JobRewardModifier", "sluggish", start);
        assertEquals(2, capture.warnings.size());
    }

    @Test
    void thresholdZeroDisablesMeasurementEntirely() {
        Capture capture = new Capture();
        AtomicLong clock = new AtomicLong(ms(1));
        SlowExtensionReporter r = new SlowExtensionReporter(logger(capture), 0, clock::get);

        assertFalse(r.enabled());
        assertEquals(0L, r.now(), "無効時は nanoTime を読まない");
        clock.addAndGet(ms(5_000));
        r.reportIfSlow("JobRewardModifier", "anything", 0L);
        assertTrue(capture.warnings.isEmpty());
    }
}
