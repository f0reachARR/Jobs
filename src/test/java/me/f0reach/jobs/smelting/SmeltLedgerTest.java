package me.f0reach.jobs.smelting;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 精錬元帳の帰属ロジック。ADR-0024 を参照。
 */
class SmeltLedgerTest {

    private static final NamespacedKey RAW_IRON = NamespacedKey.minecraft("raw_iron");
    private static final NamespacedKey RAW_GOLD = NamespacedKey.minecraft("raw_gold");

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    void syncCreditsIncreaseToToucher() {
        SmeltLedger ledger = SmeltLedger.empty();
        ledger.sync(RAW_IRON, 8, ALICE);

        assertEquals(8, ledger.total());
        assertEquals(List.of(new SmeltLedger.Entry(ALICE, 8)), ledger.entries());
        assertEquals(RAW_IRON, ledger.itemKey());
        assertTrue(ledger.isDirty());
    }

    /** 差分が無ければ元帳は変わらない。PDC への無駄な書き込みを避ける。 */
    @Test
    void syncWithoutDiffKeepsLedgerClean() {
        SmeltLedger ledger = SmeltLedger.of(RAW_IRON, List.of(new SmeltLedger.Entry(ALICE, 8)));
        ledger.markClean();

        ledger.sync(RAW_IRON, 8, BOB);

        assertFalse(ledger.isDirty());
        assertEquals(List.of(new SmeltLedger.Entry(ALICE, 8)), ledger.entries());
    }

    /** 自動投入は所有者 null で、順序を保って積む。省くと後続の帰属がずれる。 */
    @Test
    void syncKeepsAutomationEntryInOrder() {
        SmeltLedger ledger = SmeltLedger.empty();
        ledger.sync(RAW_IRON, 8, ALICE);
        ledger.sync(RAW_IRON, 64, null);

        assertEquals(
                List.of(new SmeltLedger.Entry(ALICE, 8), new SmeltLedger.Entry(null, 56)),
                ledger.entries());
    }

    @Test
    void syncMergesConsecutiveSameOwner() {
        SmeltLedger ledger = SmeltLedger.empty();
        ledger.sync(RAW_IRON, 8, ALICE);
        ledger.sync(RAW_IRON, 20, ALICE);

        assertEquals(List.of(new SmeltLedger.Entry(ALICE, 20)), ledger.entries());
    }

    /** 取り出しは新しいエントリから削る。 */
    @Test
    void syncRemovesFromTailOnWithdrawal() {
        SmeltLedger ledger = SmeltLedger.of(
                RAW_IRON,
                List.of(new SmeltLedger.Entry(ALICE, 8), new SmeltLedger.Entry(BOB, 10)));

        ledger.sync(RAW_IRON, 10, ALICE);

        assertEquals(
                List.of(new SmeltLedger.Entry(ALICE, 8), new SmeltLedger.Entry(BOB, 2)),
                ledger.entries());
    }

    @Test
    void syncClearsWhenSlotEmptied() {
        SmeltLedger ledger = SmeltLedger.of(RAW_IRON, List.of(new SmeltLedger.Entry(ALICE, 8)));

        ledger.sync(null, 0, ALICE);

        assertTrue(ledger.isEmpty());
        assertNull(ledger.itemKey());
    }

    /** 別の材料に入れ替えたら前の帰属は引き継がない。 */
    @Test
    void syncReplacesLedgerOnItemChange() {
        SmeltLedger ledger = SmeltLedger.of(RAW_IRON, List.of(new SmeltLedger.Entry(ALICE, 8)));

        ledger.sync(RAW_GOLD, 4, BOB);

        assertEquals(RAW_GOLD, ledger.itemKey());
        assertEquals(List.of(new SmeltLedger.Entry(BOB, 4)), ledger.entries());
    }

    @Test
    void consumeOnePaysOldestFirst() {
        SmeltLedger ledger = SmeltLedger.of(
                RAW_IRON,
                List.of(new SmeltLedger.Entry(ALICE, 2), new SmeltLedger.Entry(BOB, 1)));

        assertEquals(ALICE, ledger.consumeOne(RAW_IRON));
        assertEquals(ALICE, ledger.consumeOne(RAW_IRON));
        assertEquals(BOB, ledger.consumeOne(RAW_IRON));
        assertTrue(ledger.isEmpty());
        assertNull(ledger.itemKey());
    }

    /** 自動投入ぶんは支払わずに消費だけ進む。 */
    @Test
    void consumeOneReturnsNullForAutomationEntry() {
        SmeltLedger ledger = SmeltLedger.of(
                RAW_IRON,
                List.of(new SmeltLedger.Entry(null, 1), new SmeltLedger.Entry(ALICE, 1)));

        assertNull(ledger.consumeOne(RAW_IRON));
        assertEquals(ALICE, ledger.consumeOne(RAW_IRON));
    }

    @Test
    void consumeOneOnEmptyLedgerPaysNobody() {
        assertNull(SmeltLedger.empty().consumeOne(RAW_IRON));
    }

    /** 記録と違う材料が焼けたら元帳を破棄する。取りこぼしを他人の帰属で払わない。 */
    @Test
    void consumeOneDiscardsLedgerOnItemMismatch() {
        SmeltLedger ledger = SmeltLedger.of(RAW_IRON, List.of(new SmeltLedger.Entry(ALICE, 8)));

        assertNull(ledger.consumeOne(RAW_GOLD));
        assertTrue(ledger.isEmpty());
    }

    /** 上限を超えたら最古の 2 件を古い側へ合算する。合計は保たれる。 */
    @Test
    void creditEnforcesEntryLimit() {
        SmeltLedger ledger = SmeltLedger.empty();
        int total = 0;
        for (int i = 0; i < SmeltLedger.MAX_ENTRIES + 3; i++) {
            // 所有者を交互に変えて合算されないようにする。
            total += 1;
            ledger.sync(RAW_IRON, total, i % 2 == 0 ? ALICE : BOB);
        }

        assertEquals(SmeltLedger.MAX_ENTRIES, ledger.entries().size());
        assertEquals(total, ledger.total());
        assertEquals(ALICE, ledger.entries().get(0).owner());
    }
}
