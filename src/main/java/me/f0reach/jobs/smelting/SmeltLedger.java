package me.f0reach.jobs.smelting;

import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * かまど 1 個ぶんの精錬元帳。「誰の何個が精錬待ちか」を投入順に持つ。
 *
 * <p>spec/05-persistence.md 「精錬元帳（Block PDC）」および
 * spec/adr/0024-smelt-ledger-on-block-pdc.md を参照。
 *
 * <p>Bukkit の状態には触らない純ロジックで、永続化は {@link FurnaceLedgerStore} が担う。
 * 合計は入力スロットの実個数へ {@link #sync} で同期させる前提で、
 * イベントは「直前に触ったのは誰か」のヒントとしてのみ使う。
 */
public final class SmeltLedger {

    /** エントリ数の上限。超えたら最古の 2 件を古い側の所有者へ合算する。 */
    public static final int MAX_ENTRIES = 8;

    /** 1 エントリあたりの個数上限（codec の 2 バイト表現に合わせる）。 */
    public static final int MAX_COUNT = 0xFFFF;

    /**
     * 元帳の 1 エントリ。
     *
     * @param owner 投入者。null は hopper / dispenser / dropper 由来の自動投入で、報酬の対象外。
     * @param count 精錬待ちの個数。
     */
    public record Entry(UUID owner, int count) {
        public Entry {
            if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        }
    }

    private NamespacedKey itemKey;
    private final List<Entry> entries;
    private boolean dirty;

    private SmeltLedger(NamespacedKey itemKey, List<Entry> entries) {
        this.itemKey = itemKey;
        this.entries = new ArrayList<>(entries);
        if (this.entries.isEmpty()) this.itemKey = null;
    }

    public static SmeltLedger empty() {
        return new SmeltLedger(null, List.of());
    }

    /** codec とテストから使う。entries は投入順（先頭が最古）。 */
    public static SmeltLedger of(NamespacedKey itemKey, List<Entry> entries) {
        return new SmeltLedger(itemKey, entries);
    }

    /** 入力スロットのアイテム種別。空の元帳では null。 */
    public NamespacedKey itemKey() {
        return itemKey;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int total() {
        int sum = 0;
        for (Entry e : entries) sum += e.count();
        return sum;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 前回の永続化以降に内容が変わったか。{@link FurnaceLedgerStore} が無駄な書き込みを避けるために見る。 */
    public boolean isDirty() {
        return dirty;
    }

    /** 永続化した直後に呼ぶ。 */
    public void markClean() {
        this.dirty = false;
    }

    public void clear() {
        if (entries.isEmpty() && itemKey == null) return;
        entries.clear();
        itemKey = null;
        dirty = true;
    }

    /**
     * 入力スロットの実態へ合わせる。投入系イベントの 1 tick 後に呼ぶ。
     *
     * <p>増分は {@code attributeTo} へ帰属させ、減分は新しいエントリから削る。
     * 実個数が 0 か、アイテム種別が元帳の記録と違えば、前の帰属は引き継がない。
     *
     * @param actualItemKey 入力スロットのアイテム種別。空スロットなら null。
     * @param actualCount   入力スロットの実個数。
     * @param attributeTo   増分の帰属先。null は自動投入（報酬の対象外）。
     */
    public void sync(NamespacedKey actualItemKey, int actualCount, UUID attributeTo) {
        if (actualItemKey == null || actualCount <= 0) {
            clear();
            return;
        }
        if (!actualItemKey.equals(itemKey)) {
            // 別の材料に入れ替わった。元帳ごと差し替える。
            entries.clear();
            itemKey = actualItemKey;
            credit(attributeTo, actualCount);
            return;
        }
        int diff = actualCount - total();
        if (diff > 0) credit(attributeTo, diff);
        else if (diff < 0) removeFromTail(-diff);
    }

    /**
     * 精錬 1 個ぶんを先頭から引き当てる。
     *
     * @param actualItemKey 精錬された材料の種別。元帳の記録と違えば元帳を破棄する。
     * @return 支払い先。null なら支払わない（自動投入ぶん、元帳が空、種別不一致のいずれか）。
     */
    public UUID consumeOne(NamespacedKey actualItemKey) {
        if (actualItemKey != null && itemKey != null && !actualItemKey.equals(itemKey)) {
            clear();
            return null;
        }
        if (entries.isEmpty()) return null;
        Entry head = entries.get(0);
        if (head.count() <= 1) entries.remove(0);
        else entries.set(0, new Entry(head.owner(), head.count() - 1));
        if (entries.isEmpty()) itemKey = null;
        dirty = true;
        return head.owner();
    }

    /** 末尾に積む。直前のエントリと同じ所有者なら合算する。 */
    private void credit(UUID owner, int count) {
        if (count <= 0) return;
        dirty = true;
        if (!entries.isEmpty()) {
            Entry last = entries.get(entries.size() - 1);
            if (sameOwner(last.owner(), owner)) {
                entries.set(entries.size() - 1, new Entry(owner, clampCount(last.count() + count)));
                return;
            }
        }
        entries.add(new Entry(owner, clampCount(count)));
        enforceEntryLimit();
    }

    /**
     * 取り出しぶんを新しいエントリから削る。
     *
     * <p>取り出したのが誰であれ新しい側から削るので、複数人が投入した元帳では帰属が入れ替わり得る。
     * ただし支払いの総量は実際に精錬された個数で上限が付くため、これで総支払いが増えることはない。
     */
    private void removeFromTail(int count) {
        int remaining = count;
        while (remaining > 0 && !entries.isEmpty()) {
            dirty = true;
            int lastIndex = entries.size() - 1;
            Entry last = entries.get(lastIndex);
            if (last.count() <= remaining) {
                entries.remove(lastIndex);
                remaining -= last.count();
            } else {
                entries.set(lastIndex, new Entry(last.owner(), last.count() - remaining));
                remaining = 0;
            }
        }
        if (entries.isEmpty()) itemKey = null;
    }

    /** 上限を超えたら最古の 2 件を古い側の所有者へ合算する。 */
    private void enforceEntryLimit() {
        while (entries.size() > MAX_ENTRIES) {
            Entry oldest = entries.get(0);
            Entry next = entries.get(1);
            entries.remove(1);
            entries.set(0, new Entry(oldest.owner(), clampCount(oldest.count() + next.count())));
        }
    }

    private static int clampCount(int count) {
        return Math.min(count, MAX_COUNT);
    }

    private static boolean sameOwner(UUID a, UUID b) {
        return a == null ? b == null : a.equals(b);
    }
}
