package me.f0reach.jobs.modifier.dailycap;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@link DailyCapEvaluator} が依存する最小 interface。
 * 実運用は {@link DailyTotalCache}、テストは stub を差し込む。
 *
 * <p>日付は呼び出し側が渡す。処理時刻から求めると、ワーカーのキューが深いときに
 * 日跨ぎしたアクションが翌日の枠へ計上される（docs/plan/async-reward-pipeline.md）。
 */
public interface DailyTotalView {

    double totalOn(UUID playerUuid, LocalDate date);

    double forJobOn(UUID playerUuid, LocalDate date, String jobId);

    void add(UUID playerUuid, LocalDate date, String jobId, double amount);
}
