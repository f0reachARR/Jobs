package me.f0reach.jobs.modifier.variety;

import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.pipeline.async.RewardDispatcher;
import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ジョブごとの単調性ペナルティを評価する。
 * spec/04-reward-pipeline.md 「variety_penalty」および class-structure.md を参照。
 *
 * <p>「(playerUuid, jobId) の ring buffer + curve lookup」を管理する。
 * ログイン時に非同期でリポジトリから最新 window 件を読み込み、ring buffer を初期化する。
 *
 * <p>ring buffer への書き込みは {@link RewardDispatcher} が保証する単一スレッドから行う
 * （docs/plan/async-reward-pipeline.md 「可変状態の扱い」）。
 * {@code /jobs status} と Dialog UI からの読み出しは main thread で起きるため、
 * map は {@link ConcurrentHashMap}、個々の buffer は自身のロックで守る。
 */
public final class VarietyPenaltyEvaluator {

    private final Plugin plugin;
    private final ActionLogRepository actionLogRepo;
    private final AsyncExecutor asyncExecutor;
    private final RewardDispatcher dispatcher;

    /** (playerUuid, jobId) → ring buffer。 */
    private final Map<Key, VarietyRingBuffer> ringBuffers = new ConcurrentHashMap<>();
    /** jobId → curve lookup キャッシュ。 */
    private final Map<String, VarietyCurveLookup> curveCache = new ConcurrentHashMap<>();

    public VarietyPenaltyEvaluator(
            Plugin plugin,
            ActionLogRepository actionLogRepo,
            AsyncExecutor asyncExecutor,
            RewardDispatcher dispatcher
    ) {
        this.plugin = plugin;
        this.actionLogRepo = actionLogRepo;
        this.asyncExecutor = asyncExecutor;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /**
     * ペナルティを計算し、ring buffer に「今回のアクションキー」を記録する。
     * variety_penalty が無効な場合は multiplier = 1.0 を返し ring buffer にも触れない。
     *
     * <p>{@link RewardDispatcher} が保証する単一スレッドから呼ばれる前提。
     */
    public Result evaluateAndRecord(UUID playerUuid, JobDefinition job, String actionKey) {
        VarietyPenaltyConfig config = job.varietyPenalty();
        if (config == null || !config.enabled()) {
            return Result.noPenalty();
        }
        Key key = new Key(playerUuid, job.id());
        VarietyRingBuffer buffer = bufferFor(key, config.window());
        // 件数と ratio は 1 回のロックでまとめて取る。個別 getter を並べると
        // 読み出しの合間に値がずれる余地が残る。
        VarietyRingBuffer.Snapshot before = buffer.snapshot();
        // buffer が window 件に満たない間は「直近 window 件」を満たさないため penalty を発動しない。
        // 記録だけ進め、buffer が埋まった以降のアクションから curve を適用する
        // （spec/02-yaml-schema.md variety_penalty、spec/04-reward-pipeline.md 内蔵 Modifier）。
        if (before.size() < before.capacity()) {
            buffer.record(actionKey);
            return Result.noPenalty();
        }
        // 今回のアクションを含めずに ratio を計算する（look-back 意味論）。
        double ratio = before.topRatio();
        double multiplier = curveFor(job.id(), config).lookup(ratio);
        buffer.record(actionKey);
        return new Result(multiplier, ratio, config.disclosedMessage(), config.hideNumbers(),
                before.size(), config.window());
    }

    /**
     * {@link JobDefinition} から window を読んで {@link #warmup(UUID, JobId, int)} する。
     * variety_penalty が無いか無効なら何もしない。
     */
    public void warmupFor(UUID playerUuid, JobDefinition job) {
        VarietyPenaltyConfig config = job.varietyPenalty();
        if (config == null || !config.enabled()) return;
        warmup(playerUuid, job.id(), config.window());
    }

    /**
     * プレイヤー参加時に、現在の専業について ring buffer を async でウォームアップする。
     * 事前に呼ぶ jobId は現在の専業のみ。未選択なら何もしない。
     */
    public void warmup(UUID playerUuid, JobId jobId, int window) {
        if (window <= 0) return;
        Key key = new Key(playerUuid, jobId);
        asyncExecutor.runAsync(() -> {
            List<String> recent;
            try {
                recent = actionLogRepo.recentKeys(playerUuid, jobId.value(), window);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "variety warmup failed for " + playerUuid + "/" + jobId.value(), e);
                return;
            }
            dispatcher.dispatchControl(() -> bufferFor(key, window).initFromRecent(recent));
        });
    }

    /** プレイヤー切断時に memory を解放する。 */
    public void unload(UUID playerUuid) {
        dispatcher.dispatchControl(
                () -> ringBuffers.keySet().removeIf(k -> k.playerUuid().equals(playerUuid)));
    }

    /** 現時点で保持している buffer の snapshot。UI 用途。null なら未初期化。 */
    public Snapshot snapshot(UUID playerUuid, JobId jobId) {
        VarietyRingBuffer buf = ringBuffers.get(new Key(playerUuid, jobId));
        if (buf == null) return null;
        VarietyRingBuffer.Snapshot s = buf.snapshot();
        return new Snapshot(s.size(), s.capacity(), s.topRatio(), s.topKey());
    }

    /**
     * key に対応する ring buffer を返す。無ければ作る。
     * 既存 buffer の容量が window と違う場合は作り直す。
     * reload で {@code variety_penalty.window} が変われば、古い容量の buffer が残ってしまうため
     * （buffer の容量は生成時に固定される）。
     *
     * <p>呼び出しは {@link RewardDispatcher} の単一スレッドからのみなので、
     * get → put の間に別の書き手は挟まらない。
     */
    private VarietyRingBuffer bufferFor(Key key, int window) {
        VarietyRingBuffer existing = ringBuffers.get(key);
        if (existing != null && existing.capacity() == window) {
            return existing;
        }
        VarietyRingBuffer fresh = new VarietyRingBuffer(window);
        ringBuffers.put(key, fresh);
        return fresh;
    }

    private VarietyCurveLookup curveFor(JobId jobId, VarietyPenaltyConfig config) {
        return curveCache.computeIfAbsent(jobId.value(), k -> new VarietyCurveLookup(config.curve()));
    }

    /** curve キャッシュを破棄する。reload 時に呼ぶ想定。 */
    public void invalidateCurves() {
        curveCache.clear();
    }

    /**
     * ring buffer を全破棄する。reload 時に呼ぶ想定。
     * 破棄後は online player ぶんを {@link #warmupFor} で作り直す（JobsServices#reload）。
     */
    public void invalidateBuffers() {
        dispatcher.dispatchControl(ringBuffers::clear);
    }

    private record Key(UUID playerUuid, JobId jobId) {
        Key {
            Objects.requireNonNull(playerUuid, "playerUuid");
            Objects.requireNonNull(jobId, "jobId");
        }
    }

    /**
     * 評価結果。
     *
     * @param multiplier      報酬倍率。1.0 でペナルティなし。
     * @param topRatio        評価に用いた最多キー比率。
     * @param disclosedMessage プレイヤーへ見せる MiniMessage 文字列。null / 空なら通知しない。
     * @param hideNumbers     UI で数値を隠すかどうか。
     * @param bufferSize      評価時点の ring buffer 件数（look-back 分）。
     * @param bufferCapacity  ring buffer の容量（config.window）。
     */
    public record Result(
            double multiplier,
            double topRatio,
            String disclosedMessage,
            boolean hideNumbers,
            int bufferSize,
            int bufferCapacity
    ) {
        public static Result noPenalty() {
            return new Result(1.0, 0.0, null, false, 0, 0);
        }

        public boolean isPenalized() {
            return multiplier < 1.0;
        }
    }

    /** UI / /jobs status 用の snapshot。 */
    public record Snapshot(int size, int capacity, double topRatio, String topKey) {}
}
