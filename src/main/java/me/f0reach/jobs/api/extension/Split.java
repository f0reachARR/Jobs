package me.f0reach.jobs.api.extension;

import java.util.List;

/**
 * {@link JobRewardSplitter#split(JobRewardSplitContext)} の戻り値。
 * spec/06-public-api.md 「JobRewardSplitter」を参照。
 *
 * @param deductedFromPlayer プレイヤーから差し引く額 (未丸め、0 以上)。
 * @param transfers          差し引いた額の振り分け先（監査ログ用）。
 */
public record Split(double deductedFromPlayer, List<Transfer> transfers) {

    public Split {
        if (deductedFromPlayer < 0) {
            throw new IllegalArgumentException("deductedFromPlayer must be >= 0");
        }
        transfers = transfers == null ? List.of() : List.copyOf(transfers);
    }

    /** 差し引かないヘルパ。 */
    public static Split none() {
        return new Split(0.0, List.of());
    }
}
