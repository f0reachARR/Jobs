package me.f0reach.jobs.modifier.dailycap;

import java.util.UUID;

/**
 * {@link DailyCapEvaluator} が依存する最小 interface。
 * 実運用は {@link DailyTotalCache}、テストは stub を差し込む。
 */
public interface DailyTotalView {

    double todayTotal(UUID playerUuid);

    double todayForJob(UUID playerUuid, String jobId);

    void add(UUID playerUuid, String jobId, double amount);
}
