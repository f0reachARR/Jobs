package me.f0reach.jobs.persistence.async;

import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.DailyRewardTotalRepository;
import me.f0reach.jobs.persistence.dto.ActionLogRow;
import me.f0reach.jobs.persistence.dto.DailyRewardDelta;
import org.bukkit.plugin.Plugin;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * 単一 daemon スレッドで動く action_log の batch flush worker。
 * 1 秒または 1000 件ごとに INSERT を発行する。
 *
 * <p>spec/05-persistence.md 「書き込みのスレッドモデル」および threading.md を参照。
 * 連続失敗 5 回で {@link #backpressure} を立て、ActionLogStage 側で enqueue を諦める判断材料にする。
 */
public final class BatchFlushWorker implements Runnable {

    private static final int BATCH_SIZE = 1000;
    private static final long FLUSH_INTERVAL_MS = 1000;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final Plugin plugin;
    private final ActionLogWriteQueue queue;
    private final ActionLogRepository actionLogRepo;
    private final DailyRewardTotalRepository dailyRepo;
    private final ZoneId zone;

    private volatile boolean running = true;
    private volatile boolean backpressure = false;
    private Thread thread;

    public BatchFlushWorker(
            Plugin plugin,
            ActionLogWriteQueue queue,
            ActionLogRepository actionLogRepo,
            DailyRewardTotalRepository dailyRepo,
            ZoneId zone
    ) {
        this.plugin = plugin;
        this.queue = queue;
        this.actionLogRepo = actionLogRepo;
        this.dailyRepo = dailyRepo;
        this.zone = zone;
    }

    public void start() {
        thread = new Thread(this, "Jobs-BatchFlush");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isBackpressure() {
        return backpressure;
    }

    /**
     * {@code /jobs admin flush} 用。worker を止めずに現在キューに積まれている行を全て drain して INSERT する。
     * 呼び出しはリクエスト元スレッドで同期に走る（AsyncExecutor 経由で呼ぶこと）。
     *
     * @return flush した行数（0 なら enqueue 済みが無かった）
     */
    public int flushNow() {
        List<ActionLogRow> remaining = queue.drainAll();
        if (remaining.isEmpty()) return 0;
        flushBatch(remaining);
        return remaining.size();
    }

    /** shutdown 時に残キューを timeout まで drain する。 */
    public void drainAndStop(long timeoutMs) {
        running = false;
        if (thread == null) return;
        long deadline = System.currentTimeMillis() + timeoutMs;
        // 追加分を先に flush する。
        List<ActionLogRow> remaining = queue.drainAll();
        if (!remaining.isEmpty()) {
            flushBatch(remaining);
        }
        try {
            long remainingWait = Math.max(0, deadline - System.currentTimeMillis());
            thread.join(remainingWait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        int consecutiveFailures = 0;
        while (running) {
            try {
                List<ActionLogRow> batch = queue.drain(BATCH_SIZE, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (batch.isEmpty()) continue;
                boolean ok = flushBatch(batch);
                if (ok) {
                    consecutiveFailures = 0;
                    backpressure = false;
                } else {
                    consecutiveFailures++;
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        backpressure = true;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "BatchFlushWorker crashed loop iteration", e);
            }
        }
    }

    /** 成功したら true。 */
    private boolean flushBatch(List<ActionLogRow> batch) {
        try {
            actionLogRepo.insertBatch(batch);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "action_log insertBatch failed (" + batch.size() + " rows)", e);
            return false;
        }
        try {
            List<DailyRewardDelta> deltas = mergeDailyDeltas(batch);
            if (!deltas.isEmpty()) dailyRepo.addBatch(deltas);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "daily_reward_total addBatch failed", e);
            // action_log 側は成功なので、失敗扱いにはしない。
        }
        return true;
    }

    private List<DailyRewardDelta> mergeDailyDeltas(List<ActionLogRow> batch) {
        Map<DailyKey, Double> merged = new HashMap<>();
        for (ActionLogRow row : batch) {
            if (row.finalReward() <= 0.0) continue;
            var date = row.occurredAt().atZone(zone).toLocalDate();
            merged.merge(new DailyKey(row.playerUuid(), date), row.finalReward(), Double::sum);
        }
        List<DailyRewardDelta> out = new ArrayList<>(merged.size());
        for (Map.Entry<DailyKey, Double> e : merged.entrySet()) {
            out.add(new DailyRewardDelta(e.getKey().player(), e.getKey().date(), e.getValue()));
        }
        return out;
    }

    private record DailyKey(java.util.UUID player, java.time.LocalDate date) {}
}
