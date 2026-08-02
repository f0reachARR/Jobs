package me.f0reach.jobs.command.admin;

import me.f0reach.jobs.antiautomation.ContainerKind;
import me.f0reach.jobs.kvs.KvsKeys;
import me.f0reach.jobs.ui.DialogTexts;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KVS の値を key prefix から読み下す変換。
 * OperatorTracker のエンコードと 1:1 で対応している必要がある。
 */
class KvsValueDescriberTest {

    private static final UUID WORLD = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private static final Function<UUID, String> UNKNOWN_NAMES = uuid -> null;
    private static final Function<UUID, String> KNOWN_NAMES =
            Map.of(PLAYER, "Steve")::get;

    /** OperatorTracker#encodePlayer と同じレイアウト (marker 1 byte + UUID 16 byte)。 */
    private static byte[] encodePlayer(UUID uuid) {
        return ByteBuffer.allocate(17)
                .put((byte) 1)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    @Test
    void placeKeyIsDescribedAsPlacedMarker() {
        KvsValueDescriber.Described d = KvsValueDescriber.describe(
                KvsKeys.place(WORLD, 1, 2, 3), new byte[]{1}, UNKNOWN_NAMES);
        assertEquals(DialogTexts.COMMAND_ADMIN_KVS_VALUE_PLACED, d.textKey());
        assertEquals("", d.value());
    }

    @Test
    void tradeKeyIsDescribedAsTradedMarker() {
        KvsValueDescriber.Described d = KvsValueDescriber.describe(
                KvsKeys.trade(PLAYER, 0), new byte[]{1}, UNKNOWN_NAMES);
        assertEquals(DialogTexts.COMMAND_ADMIN_KVS_VALUE_TRADED, d.textKey());
    }

    @Test
    void opKeyWithPlayerOperatorResolvesName() {
        KvsValueDescriber.Described d = KvsValueDescriber.describe(
                KvsKeys.op(ContainerKind.FURNACE.tag(), WORLD, 1, 2, 3),
                encodePlayer(PLAYER), KNOWN_NAMES);
        assertEquals(DialogTexts.COMMAND_ADMIN_KVS_VALUE_OPERATOR_PLAYER, d.textKey());
        assertEquals("Steve", d.value());
    }

    @Test
    void opKeyFallsBackToUuidWhenNameIsUnknown() {
        KvsValueDescriber.Described d = KvsValueDescriber.describe(
                KvsKeys.op(ContainerKind.FURNACE.tag(), WORLD, 1, 2, 3),
                encodePlayer(PLAYER), UNKNOWN_NAMES);
        assertEquals(PLAYER.toString(), d.value());
    }

    @Test
    void opKeyWithNullOperatorIsDescribedAsAutomation() {
        // marker=0 は hopper / dispenser 由来。auto_fed_processing が 0 判定する側。
        KvsValueDescriber.Described d = KvsValueDescriber.describe(
                KvsKeys.op(ContainerKind.BREWING_STAND.tag(), WORLD, 1, 2, 3),
                new byte[]{0}, KNOWN_NAMES);
        assertEquals(DialogTexts.COMMAND_ADMIN_KVS_VALUE_OPERATOR_AUTOMATION, d.textKey());
    }

    @Test
    void truncatedOperatorPayloadIsTreatedAsAutomation() {
        // 壊れた値で誤って「Player が投入した」と読ませない。
        KvsValueDescriber.Described d = KvsValueDescriber.describe(
                KvsKeys.op(ContainerKind.SMOKER.tag(), WORLD, 1, 2, 3),
                new byte[]{1, 2, 3}, KNOWN_NAMES);
        assertEquals(DialogTexts.COMMAND_ADMIN_KVS_VALUE_OPERATOR_AUTOMATION, d.textKey());
    }

    @Test
    void unknownPrefixFallsBackToHex() {
        KvsValueDescriber.Described d = KvsValueDescriber.describe(
                "future:something", new byte[]{0x0a, (byte) 0xff}, UNKNOWN_NAMES);
        assertEquals(DialogTexts.COMMAND_ADMIN_KVS_VALUE_UNKNOWN, d.textKey());
        assertEquals("0aff", d.value());
    }

    @Test
    void hexDumpIsTruncatedForLongValues() {
        byte[] long_ = new byte[40];
        String hex = KvsValueDescriber.toHex(long_);
        assertTrue(hex.startsWith("0".repeat(32)), () -> "unexpected hex: " + hex);
        assertTrue(hex.contains("40 bytes"), () -> "length hint missing: " + hex);
    }

    @Test
    void emptyValueIsReported() {
        assertEquals("(empty)", KvsValueDescriber.toHex(new byte[0]));
    }
}
