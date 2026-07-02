package me.f0reach.jobs.modifier.variety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VarietyRingBufferTest {

    @Test
    void emptyBufferReportsZeroRatio() {
        VarietyRingBuffer buf = new VarietyRingBuffer(5);
        assertEquals(0.0, buf.topRatio());
        assertNull(buf.topKey());
        assertTrue(buf.isEmpty());
    }

    @Test
    void recordAndCount() {
        VarietyRingBuffer buf = new VarietyRingBuffer(5);
        buf.record("kill:zombie");
        buf.record("kill:zombie");
        buf.record("kill:skeleton");
        assertEquals(3, buf.size());
        assertEquals(2.0 / 3.0, buf.topRatio());
        assertEquals("kill:zombie", buf.topKey());
    }

    @Test
    void ringDropsOldestAtCapacity() {
        VarietyRingBuffer buf = new VarietyRingBuffer(3);
        buf.record("A");
        buf.record("A");
        buf.record("B");
        buf.record("C");
        // 最古の A が押し出される → 残り [A, B, C] → 3 分の 1 が最多
        assertEquals(3, buf.size());
        assertEquals(1.0 / 3.0, buf.topRatio());
    }

    @Test
    void initFromRecentUsesNewestFirstOrdering() {
        // recentKeys は「新しい順」で渡ってくる。ring buffer の内部は「古い順」で保持する。
        VarietyRingBuffer buf = new VarietyRingBuffer(3);
        buf.initFromRecent(List.of("newest", "middle", "oldest"));
        assertEquals(3, buf.size());
        buf.record("appended");
        // 一番古い oldest が押し出され、[middle, newest, appended] に。
        assertEquals(1.0 / 3.0, buf.topRatio());
    }

    @Test
    void initTruncatesToCapacity() {
        VarietyRingBuffer buf = new VarietyRingBuffer(2);
        buf.initFromRecent(List.of("A", "A", "B", "B"));
        // 新しい 2 件 = [A, A]。
        assertEquals(2, buf.size());
        assertEquals(1.0, buf.topRatio());
        assertEquals("A", buf.topKey());
    }
}
