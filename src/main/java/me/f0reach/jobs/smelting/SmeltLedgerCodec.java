package me.f0reach.jobs.smelting;

import org.bukkit.NamespacedKey;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link SmeltLedger} と PDC の {@code BYTE_ARRAY} を相互変換する。
 *
 * <pre>
 * version(1) | item key 長(2) | item key(UTF-8) | entry 数(1) | entry × N
 *
 * entry: marker(1) | UUID(16, marker=0x01 のときのみ) | count(2, unsigned)
 * </pre>
 *
 * spec/05-persistence.md 「精錬元帳（Block PDC）」を参照。
 * 壊れた値や未知の version は空の元帳として扱う（読めない履歴で報酬を止めない）。
 */
final class SmeltLedgerCodec {

    static final byte VERSION = 1;

    private static final byte MARKER_AUTOMATION = 0x00;
    private static final byte MARKER_PLAYER = 0x01;

    private SmeltLedgerCodec() {}

    /** 空の元帳は null を返す。呼び出し側は PDC から key を消す。 */
    static byte[] encode(SmeltLedger ledger) {
        if (ledger == null || ledger.isEmpty() || ledger.itemKey() == null) return null;
        byte[] itemKey = ledger.itemKey().toString().getBytes(StandardCharsets.UTF_8);
        List<SmeltLedger.Entry> entries = ledger.entries();

        int size = 1 + 2 + itemKey.length + 1;
        for (SmeltLedger.Entry e : entries) {
            size += 1 + 2 + (e.owner() == null ? 0 : 16);
        }

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(VERSION);
        buf.putShort((short) itemKey.length);
        buf.put(itemKey);
        buf.put((byte) Math.min(entries.size(), 0xFF));
        for (SmeltLedger.Entry e : entries) {
            if (e.owner() == null) {
                buf.put(MARKER_AUTOMATION);
            } else {
                buf.put(MARKER_PLAYER);
                buf.putLong(e.owner().getMostSignificantBits());
                buf.putLong(e.owner().getLeastSignificantBits());
            }
            buf.putShort((short) Math.min(e.count(), SmeltLedger.MAX_COUNT));
        }
        return buf.array();
    }

    static SmeltLedger decode(byte[] raw) {
        if (raw == null || raw.length == 0) return SmeltLedger.empty();
        try {
            ByteBuffer buf = ByteBuffer.wrap(raw);
            if (buf.get() != VERSION) return SmeltLedger.empty();
            int keyLength = buf.getShort() & 0xFFFF;
            byte[] keyBytes = new byte[keyLength];
            buf.get(keyBytes);
            NamespacedKey itemKey = NamespacedKey.fromString(new String(keyBytes, StandardCharsets.UTF_8));
            if (itemKey == null) return SmeltLedger.empty();

            int entryCount = buf.get() & 0xFF;
            List<SmeltLedger.Entry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                byte marker = buf.get();
                UUID owner = null;
                if (marker == MARKER_PLAYER) {
                    long hi = buf.getLong();
                    long lo = buf.getLong();
                    owner = new UUID(hi, lo);
                } else if (marker != MARKER_AUTOMATION) {
                    return SmeltLedger.empty();
                }
                int count = buf.getShort() & 0xFFFF;
                if (count > 0) entries.add(new SmeltLedger.Entry(owner, count));
            }
            return SmeltLedger.of(itemKey, entries);
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            return SmeltLedger.empty();
        }
    }
}
