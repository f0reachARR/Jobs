package me.f0reach.jobs.smelting;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * 精錬元帳をかまどブロックの PDC に読み書きする。
 *
 * <p>spec/05-persistence.md 「精錬元帳（Block PDC）」を参照。
 *
 * <p>{@code Block#getState(false)} で非スナップショットの {@code TileState} を取り、
 * スナップショットのコピーと書き戻しを避ける。書き込み後は
 * {@code BlockState#update(true, false)} を呼んでチャンクへ保存させる。
 *
 * <p>PDC は main thread からしか触れない。報酬パイプラインの段階 4 以降（ワーカー）から
 * 呼んではならない（ADR-0021）。
 */
public final class FurnaceLedgerStore {

    private static final String LEDGER_KEY = "smelt_ledger";

    private final NamespacedKey key;

    public FurnaceLedgerStore(Plugin plugin) {
        this.key = new NamespacedKey(plugin, LEDGER_KEY);
    }

    /** かまど以外のブロックや読めない値では空の元帳を返す。 */
    public SmeltLedger load(Block block) {
        Furnace furnace = furnaceState(block);
        if (furnace == null) return SmeltLedger.empty();
        SmeltLedger ledger = SmeltLedgerCodec.decode(
                furnace.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY));
        ledger.markClean();
        return ledger;
    }

    /**
     * 変更があったときだけ書く。空になった元帳は key ごと消す。
     *
     * @return 実際に書き込んだか。
     */
    public boolean save(Block block, SmeltLedger ledger) {
        if (ledger == null || !ledger.isDirty()) return false;
        Furnace furnace = furnaceState(block);
        if (furnace == null) return false;

        byte[] encoded = SmeltLedgerCodec.encode(ledger);
        var container = furnace.getPersistentDataContainer();
        if (encoded == null) {
            if (!container.has(key, PersistentDataType.BYTE_ARRAY)) {
                ledger.markClean();
                return false;
            }
            container.remove(key);
        } else {
            container.set(key, PersistentDataType.BYTE_ARRAY, encoded);
        }
        furnace.update(true, false);
        ledger.markClean();
        return true;
    }

    /** 管理コマンドや診断から使う。 */
    public NamespacedKey key() {
        return key;
    }

    private static Furnace furnaceState(Block block) {
        if (block == null) return null;
        return block.getState(false) instanceof Furnace furnace ? furnace : null;
    }
}
