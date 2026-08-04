package me.f0reach.jobs.smelting;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmeltLedgerCodecTest {

    private static final NamespacedKey RAW_IRON = NamespacedKey.minecraft("raw_iron");
    private static final UUID ALICE = UUID.randomUUID();

    @Test
    void roundTripsPlayerAndAutomationEntries() {
        SmeltLedger original = SmeltLedger.of(
                RAW_IRON,
                List.of(new SmeltLedger.Entry(ALICE, 8), new SmeltLedger.Entry(null, 56)));

        SmeltLedger decoded = SmeltLedgerCodec.decode(SmeltLedgerCodec.encode(original));

        assertEquals(RAW_IRON, decoded.itemKey());
        assertEquals(original.entries(), decoded.entries());
        assertEquals(64, decoded.total());
    }

    @Test
    void encodesEmptyLedgerAsNull() {
        assertNull(SmeltLedgerCodec.encode(SmeltLedger.empty()));
    }

    @Test
    void decodesNullAndEmptyAsEmptyLedger() {
        assertTrue(SmeltLedgerCodec.decode(null).isEmpty());
        assertTrue(SmeltLedgerCodec.decode(new byte[0]).isEmpty());
    }

    /** 未知の version は空として扱い、報酬を止めずに作り直させる。 */
    @Test
    void decodesUnknownVersionAsEmptyLedger() {
        byte[] raw = SmeltLedgerCodec.encode(
                SmeltLedger.of(RAW_IRON, List.of(new SmeltLedger.Entry(ALICE, 8))));
        raw[0] = (byte) 0x7F;

        assertTrue(SmeltLedgerCodec.decode(raw).isEmpty());
    }

    @Test
    void decodesTruncatedValueAsEmptyLedger() {
        byte[] raw = SmeltLedgerCodec.encode(
                SmeltLedger.of(RAW_IRON, List.of(new SmeltLedger.Entry(ALICE, 8))));
        byte[] truncated = new byte[raw.length - 5];
        System.arraycopy(raw, 0, truncated, 0, truncated.length);

        assertTrue(SmeltLedgerCodec.decode(truncated).isEmpty());
    }

    /** 長さが実データと合っていない値も空として扱う。 */
    @Test
    void decodesGarbageAsEmptyLedger() {
        assertTrue(SmeltLedgerCodec.decode(new byte[] { 1, 0x7F, (byte) 0xFF, 5 }).isEmpty());
        assertTrue(SmeltLedgerCodec.decode(new byte[] { 1, 0, 1, ':', 1 }).isEmpty());
    }
}
