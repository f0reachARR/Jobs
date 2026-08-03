package me.f0reach.jobs.modifier.dailycap;

import me.f0reach.jobs.api.query.TimeRange;
import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.persistence.DailyRewardTotalRepository;
import me.f0reach.jobs.pipeline.async.RewardDispatcher;
import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * プレイヤーごとの当日累計報酬を in-memory で保持するキャッシュ。
 * spec/04-reward-pipeline.md 「daily_cap」および class-structure.md 「dailycap」を参照。
 *
 * <p>ログイン時に非同期でリポジトリから読み込み、パイプラインで報酬確定と同時に increment する。
 * 「日」の境界は {@link ZoneId} で決まる（BatchFlushWorker と同じ zone を使う）。
 *
 * <p>書き込みは {@link RewardDispatcher} 経由で必ず 1 スレッドに寄せる
 * （docs/plan/async-reward-pipeline.md 「可変状態の扱い」）。
 * 読み出しは UI と PlaceholderAPI から main thread で起きるため、
 * {@link ConcurrentHashMap} と volatile による安全な公開に頼りロックは取らない。
 */
public final class DailyTotalCache implements DailyTotalView {

    private final Plugin plugin;
    private final DailyRewardTotalRepository totalRepo;
    private final ActionLogRepository actionLogRepo;
    private final AsyncExecutor asyncExecutor;
    private final RewardDispatcher dispatcher;
    private final Clock clock;
    private final ZoneId zone;
    private final PluginConfig.DailyCapConfig.Scope scope;

    /** (playerUuid) → DayEntry。日が変わったら別の DayEntry に差し替える。 */
    private final Map<UUID, DayEntry> byPlayer = new ConcurrentHashMap<>();

    public DailyTotalCache(
            Plugin plugin,
            DailyRewardTotalRepository totalRepo,
            ActionLogRepository actionLogRepo,
            AsyncExecutor asyncExecutor,
            RewardDispatcher dispatcher,
            Clock clock,
            ZoneId zone,
            PluginConfig.DailyCapConfig.Scope scope
    ) {
        this.plugin = plugin;
        this.totalRepo = totalRepo;
        this.actionLogRepo = actionLogRepo;
        this.asyncExecutor = asyncExecutor;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    /**
     * プレイヤー参加時に、今日の分をリポジトリからウォームアップする（非同期）。
     * total は {@code daily_reward_total} から、per-job は {@code action_log} から取る。
     *
     * <p>JDBC は {@link AsyncExecutor} のプールで読み、cache への書き込みだけを
     * dispatcher へ流す。書き手を 1 スレッドに保ちつつ、ワーカーを I/O で止めない。
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
            dispatcher.dispatchControl(() -> {
                DayEntry entry = new DayEntry(today);
                entry.total = loadedTotal;
                entry.perJob.putAll(loadedPerJob);
                byPlayer.put(playerUuid, entry);
            });
        });
    }

    /** プレイヤー切断時に memory を解放する。 */
    public void unload(UUID playerUuid) {
        dispatcher.dispatchControl(() -> byPlayer.remove(playerUuid));
    }

    /**
     * {@code /jobs admin reset-daily-cap} 用。in-memory 側の当日累計を破棄する。
     * オフライン相手には何もしない（cache に載っていない）。
     */
    public void reset(UUID playerUuid) {
        dispatcher.dispatchControl(() -> byPlayer.remove(playerUuid));
    }

    /** 現在時刻の LocalDate。日次境界の判定に使う。 */
    public LocalDate today() {
        return Instant.now(clock).atZone(zone).toLocalDate();
    }

    /** 指定日の累計 (scope=TOTAL 用)。cache miss と日付違いは 0.0。 */
    @Override
    public double totalOn(UUID playerUuid, LocalDate date) {
        DayEntry entry = entryOn(playerUuid, date);
        return entry == null ? 0.0 : entry.total;
    }

    /** 指定日のジョブ別累計 (scope=PER_JOB 用)。cache miss と日付違いは 0.0。 */
    @Override
    public double forJobOn(UUID playerUuid, LocalDate date, String jobId) {
        DayEntry entry = entryOn(playerUuid, date);
        if (entry == null) return 0.0;
        Double v = entry.perJob.get(jobId);
        return v == null ? 0.0 : v;
    }

    /**
     * 報酬確定時に in-memory 側の累計を increment する。
     * dispatcher が保証する単一スレッドからのみ呼ばれる。
     */
    @Override
    public void add(UUID playerUuid, LocalDate date, String jobId, double amount) {
        if (amount <= 0.0) return;
        DayEntry entry = byPlayer.get(playerUuid);
        if (entry == null || !entry.date.equals(date)) {
            // 日跨ぎ時は古い entry を捨てて作り直す。occurredAt は enqueue 順に単調なので
            // 日付が巻き戻ることはない。
            entry = new DayEntry(date);
            byPlayer.put(playerUuid, entry);
        }
        entry.total += amount;
        entry.perJob.merge(jobId, amount, Double::sum);
    }

    /** cache から指定日に対応する entry を返す。日付が違えば null。 */
    private DayEntry entryOn(UUID playerUuid, LocalDate date) {
        DayEntry entry = byPlayer.get(playerUuid);
        if (entry == null) return null;
        return entry.date.equals(date) ? entry : null;
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /**
     * 1 日分の累計。
     *
     * <p>{@code total} は dispatcher の単一スレッドだけが書き、main thread から読む。
     * volatile double の読み書きは atomic なので、ロックを取らずに整合した値が見える。
     * {@code perJob} も同じ理由で {@link ConcurrentHashMap} にする。
     */
    private static final class DayEntry {
        final LocalDate date;
        volatile double total;
        final Map<String, Double> perJob = new ConcurrentHashMap<>();

        DayEntry(LocalDate date) {
            this.date = date;
        }
    }
}
