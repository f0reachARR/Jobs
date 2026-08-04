package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.kvs.JobsKVStore;
import me.f0reach.jobs.kvs.KvsKeys;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * BrewingStand への「投入者」を KVS に記録する。
 * spec/04-reward-pipeline.md 「auto_fed_processing」および ADR-0017 を参照。
 *
 * <p>Player が UI で投入したら operator = Player UUID + TTL (config.operator_ttl_sec)。
 * TTL は job YAML 由来なので、{@code /jobs reload} で変わり得る。値は書き込みのたびに
 * supplier から読み直す（listener は再登録しないため、固定値を持つと reload で古いままになる）。
 * Hopper / Dispenser 由来なら operator = null (すぐ AutoFedProcessingCheck が 0 判定する)。
 * どちらの経路でも write-through で最新値が勝つ。
 *
 * <p>かまど (Furnace / BlastFurnace / Smoker) は対象外である。投入者と個数を
 * ブロックの PDC に持つ精錬元帳へ移した (ADR-0024)。
 */
public final class OperatorTracker implements Listener {

    /** operator が Player か null かを識別するプレフィクス。null は 1 byte、UUID は 17 byte。 */
    private static final byte MARKER_NULL = 0;
    private static final byte MARKER_PLAYER = 1;

    private final JobsKVStore kvStore;
    private final IntSupplier operatorTtlSec;

    public OperatorTracker(JobsKVStore kvStore, IntSupplier operatorTtlSec) {
        this.kvStore = kvStore;
        this.operatorTtlSec = operatorTtlSec;
    }

    /** Player の UI 操作。furnace/brewingstand の input/fuel スロットに置くパターンを検出する。 */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        BlockAndKind bak = holderBlock(holder, event.getInventory().getType());
        if (bak == null) return;

        // Player が自身の inventory 側から手動で cursor を container 側へ移した場合を「投入」とみなす。
        // シフトクリックで player inventory → container、または カーソル → container のドロップ。
        boolean isPutIntoContainer = event.getRawSlot() < event.getInventory().getSize();
        boolean isShiftFromPlayer = event.isShiftClick() && event.getRawSlot() >= event.getInventory().getSize();
        if (!(isPutIntoContainer || isShiftFromPlayer)) return;

        // 「取り出し」は無視する。cursor が空でクリック位置が container 側なら投入意図なし。
        ItemStack cursor = event.getCursor();
        if (!isShiftFromPlayer && (cursor == null || cursor.getType().isAir())) return;

        UUID player = event.getWhoClicked().getUniqueId();
        String key = KvsKeys.op(
                bak.kind().tag(),
                bak.block().getWorld().getUID(),
                bak.block().getX(), bak.block().getY(), bak.block().getZ()
        );
        kvStore.put(key, encodePlayer(player), Duration.ofSeconds(operatorTtlSec.getAsInt()));
    }

    /** Hopper / Dispenser 由来のアイテム移動を捕捉して operator を null にする。 */
    @EventHandler
    public void onMove(InventoryMoveItemEvent event) {
        InventoryHolder destHolder = event.getDestination().getHolder();
        BlockAndKind bak = holderBlock(destHolder, event.getDestination().getType());
        if (bak == null) return;

        // 送信元が hopper / dispenser / dropper なら automation とみなす。
        InventoryType sourceType = event.getSource().getType();
        if (sourceType != InventoryType.HOPPER
                && sourceType != InventoryType.DISPENSER
                && sourceType != InventoryType.DROPPER) return;

        String key = KvsKeys.op(
                bak.kind().tag(),
                bak.block().getWorld().getUID(),
                bak.block().getX(), bak.block().getY(), bak.block().getZ()
        );
        kvStore.put(key, encodeNull(), Duration.ofSeconds(operatorTtlSec.getAsInt()));
    }

    /** operator (UUID / null) をエンコード。1 byte で kind、続けて UUID 16 byte。 */
    private static byte[] encodePlayer(UUID uuid) {
        ByteBuffer buf = ByteBuffer.allocate(17);
        buf.put(MARKER_PLAYER);
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
        return buf.array();
    }

    private static byte[] encodeNull() {
        return new byte[] { MARKER_NULL };
    }

    /**
     * KVS に書き込んだ operator をデコードする。null / 未登録なら null を返す。
     * ({@link AutoFedProcessingCheck} が呼ぶ)。
     */
    public static UUID decodeOperator(byte[] raw) {
        if (raw == null || raw.length == 0) return null;
        if (raw[0] == MARKER_NULL) return null;
        if (raw[0] != MARKER_PLAYER || raw.length < 17) return null;
        ByteBuffer buf = ByteBuffer.wrap(raw, 1, 16);
        long hi = buf.getLong();
        long lo = buf.getLong();
        return new UUID(hi, lo);
    }

    /** BrewingStand の inventory holder から Block + ContainerKind を取る。他の容器は null。 */
    private static BlockAndKind holderBlock(InventoryHolder holder, InventoryType type) {
        if (holder instanceof org.bukkit.block.BrewingStand stand) {
            return new BlockAndKind(stand.getBlock(), ContainerKind.BREWING_STAND);
        }
        // Fallback: holder が BlockState でない実装のために inventory 経由で location を引く。
        if (type != InventoryType.BREWING) return null;
        if (!(holder instanceof BrewerInventory bi)
                || !(bi.getHolder() instanceof org.bukkit.block.BrewingStand stand)) {
            return null;
        }
        Location loc = stand.getLocation();
        if (loc.getWorld() == null) return null;
        return new BlockAndKind(loc.getBlock(), ContainerKind.BREWING_STAND);
    }

    /** {@link ContainerKind} と Block を組で扱うためのローカル値型。 */
    private record BlockAndKind(Block block, ContainerKind kind) {}
}
