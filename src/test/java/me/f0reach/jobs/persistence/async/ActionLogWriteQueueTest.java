package me.f0reach.jobs.persistence.async;

import me.f0reach.jobs.persistence.dto.ActionLogRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionLogWriteQueueTest {

    private ActionLogRow row() {
        return new ActionLogRow(
                UUID.randomUUID(), "combat", "kill:minecraft:zombie",
                5.0, 5.0, false, 1, Instant.now()
        );
    }

    @Test
    void offerAndDrainReturnsRows() throws Exception {
        ActionLogWriteQueue q = new ActionLogWriteQueue(10);
        assertTrue(q.offer(row()));
        assertTrue(q.offer(row()));
        List<ActionLogRow> drained = q.drain(10, 50, TimeUnit.MILLISECONDS);
        assertEquals(2, drained.size());
    }

    @Test
    void drainWithNoRowsReturnsEmpty() throws Exception {
        ActionLogWriteQueue q = new ActionLogWriteQueue(10);
        List<ActionLogRow> drained = q.drain(10, 20, TimeUnit.MILLISECONDS);
        assertTrue(drained.isEmpty());
    }

    @Test
    void offerReturnsFalseWhenFull() {
        ActionLogWriteQueue q = new ActionLogWriteQueue(1);
        assertTrue(q.offer(row()));
        assertFalse(q.offer(row()));
    }

    @Test
    void drainAllTakesEverything() {
        ActionLogWriteQueue q = new ActionLogWriteQueue(10);
        q.offer(row());
        q.offer(row());
        q.offer(row());
        List<ActionLogRow> all = q.drainAll();
        assertEquals(3, all.size());
        assertEquals(0, q.size());
    }
}
