package me.f0reach.jobs.util;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * UUID <-> byte[16] の変換ヘルパ。
 * MySQL の BINARY(16) カラム用。
 */
public final class UuidBytes {
    private UuidBytes() {}

    public static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("Expected byte[16] but got " + (bytes == null ? "null" : bytes.length));
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long msb = buffer.getLong();
        long lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }
}
