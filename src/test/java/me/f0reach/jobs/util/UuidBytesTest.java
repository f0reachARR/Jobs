package me.f0reach.jobs.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UuidBytesTest {

    @Test
    void roundTrip() {
        UUID original = UUID.fromString("11111111-2222-3333-4444-555555555555");
        byte[] bytes = UuidBytes.toBytes(original);
        assertEquals(16, bytes.length);
        UUID back = UuidBytes.fromBytes(bytes);
        assertEquals(original, back);
    }

    @Test
    void randomRoundTrip() {
        for (int i = 0; i < 100; i++) {
            UUID original = UUID.randomUUID();
            assertEquals(original, UuidBytes.fromBytes(UuidBytes.toBytes(original)));
        }
    }

    @Test
    void toBytesIsMsbThenLsb() {
        UUID uuid = new UUID(0x1122334455667788L, 0x99AABBCCDDEEFF00L);
        byte[] bytes = UuidBytes.toBytes(uuid);
        byte[] expected = {
                0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88,
                (byte) 0x99, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC,
                (byte) 0xDD, (byte) 0xEE, (byte) 0xFF, 0x00
        };
        assertArrayEquals(expected, bytes);
    }

    @Test
    void rejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> UuidBytes.fromBytes(new byte[15]));
        assertThrows(IllegalArgumentException.class, () -> UuidBytes.fromBytes(null));
    }
}
