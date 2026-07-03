package me.f0reach.jobs.modifier.dailycap;

import me.f0reach.jobs.api.query.TimeRange;
import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.DailyRewardTotalRepository;
import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * プレイヤーごとの当日累計報酬を in-memory で保持するキャッシュ。
 * spec/04-reward-pipeline.md 「daily_cap」および class-structure.md 「dailycap」を参照。
 *
 * <p>ログイン時に非同期でリポジトリから読み込み、パイプラインで報酬確定と同時に increment する。
 * key は (playerUuid, LocalDate)。「日」の境界は {@link ZoneId} で決まる（BatchFlushWorker と同じ zone を使う）。
 *
 * <p>スレッドセーフではない前提で、main thread からのみアクセスする。
 * 非同期ロード完了時のみ {@link AsyncExecutor#runOnMain(Runnable)} で main thread に戻して書き込む。
 */
public final class DailyTotalCache implements DailyTotalView {

    private final Plugin plugin;
    private final DailyRewardTotalRepository totalRepo;
    private final ActionLogRepository actionLogRepo;
    private final AsyncExecutor asyncExecutor;
    private final Clock clock;
    private final ZoneId zone;
    private final PluginConfig.DailyCapConfig.Scope scope;

    /** (playerUuid) → DayEntry。日が変わったら別の DayEntry に差し替える。 */
    private final Map<UUID, DayEntry> byPlayer = new HashMap<>();

    public DailyTotalCache(
            Plugin plugin,
            DailyRewardTotalRepository totalRepo,
            ActionLogRepository actionLogRepo,
            AsyncExecutor asyncExecutor,
            Clock clock,
            ZoneId zone,
            PluginConfig.DailyCapConfig.Scope scope
    ) {
        this.plugin = plugin;
        this.totalRepo = totalRepo;
        this.actionLogRepo = actionLogRepo;
        this.asyncExecutor = asyncExecutor;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    /**
     * プレイヤー参加時に、今日の分をリポジトリからウォームアップする（非同期）。
     * total は {@code daily_reward_total} から、per-job は {@code action_log} から取る。
     */
    public void warmup(UUID playerUuid) {
        LocalDate today = today();
        asyncExecutor.runAsync(() -> {
            double total = 0.0;
            Map<String, Double> perJob = Map.of();
            try {
                total = totalRepo.getTotal(playerUuid, today);
                if (scope == PluginConfig.DailyCapConfig.Scope.PER_JOB) {
                    TimeRange range = new TimeRange(startOfDay(today), startOfDay(today.plusDays(1)));
                    perJob = actionLogRepo.sumRewardByJob(playerUuid, range);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "daily_cap warmup failed for " + playerUuid, e);
                return;
            }
            double loadedTotal = total;
            Map<String, Double> loadedPerJob = perJob;
            asyncExecutor.runOnMain(() -> {
                DayEntry entry = new DayEntry(today);
                entry.total = loadedTotal;
                entry.perJob.putAll(loadedPerJob);
                byPlayer.put(playerUuid, entry);
            });
        });
    }

    /** プレイヤー切断時に memory を解放する。 */
    public void unload(UUID playerUuid) {
        byPlayer.remove(playerUuid);
    }

    /**
     * {@code /jobs admin reset-daily-cap} 用。in-memory 側の当日累計を破棄する。
     * オフライン相手には何もしない（cache に載っていない）。
     */
    public void reset(UUID playerUuid) {
        byPlayer.remove(playerUuid);
    }

    /** 現在時刻の LocalDate。日次境界の判定に使う。 */
    public LocalDate today() {
        return Instant.now(clock).atZone(zone).toLocalDate();
    }

    /** 今日の累計 (scope=TOTAL 用)。cache miss は 0.0。 */
    public double todayTotal(UUID playerUuid) {
        DayEntry entry = currentEntry(playerUuid);
        return entry == null ? 0.0 : entry.total;
    }

    /** 今日のジョブ別累計 (scope=PER_JOB 用)。cache miss は 0.0。 */
    public double todayForJob(UUID playerUuid, String jobId) {
        DayEntry entry = currentEntry(playerUuid);
        if (entry == null) return 0.0;
        Double v = entry.perJob.get(jobId);
        return v == null ? 0.0 : v;
    }

    /** 報酬確定時に in-memory 側の累計を increment する。 */
    public void add(UUID playerUuid, String jobId, double amount) {
        if (amount <= 0.0) return;
        DayEntry entry = currentEntry(playerUuid);
        if (entry == null) {
            entry = new DayEntry(today());
            byPlayer.put(playerUuid, entry);
        }
        entry.total += amount;
        entry.perJob.merge(jobId, amount, Double::sum);
    }

    /** cache から現在の日にちに対応する entry を返す。日をまたいでいたら破棄する。 */
    private DayEntry currentEntry(UUID playerUuid) {
        DayEntry entry = byPlayer.get(playerUuid);
        if (entry == null) return null;
        LocalDate now = today();
        if (!entry.date.equals(now)) {
            // 日をまたいだので破棄。以降の増分は新しい entry で貯める。
            byPlayer.remove(playerUuid);
            return null;
        }
        return entry;
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    private static final class DayEntry {
        final LocalDate date;
        double total;
        final Map<String, Double> perJob = new HashMap<>();

        DayEntry(LocalDate date) {
            this.date = date;
        }
    }
}
