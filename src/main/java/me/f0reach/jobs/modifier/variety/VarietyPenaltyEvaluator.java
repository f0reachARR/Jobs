package me.f0reach.jobs.modifier.variety;

import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.persistence.ActionLogRepository;
import me.f0reach.jobs.util.AsyncExecutor;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ジョブごとの単調性ペナルティを評価する。
 * spec/04-reward-pipeline.md 「variety_penalty」および class-structure.md を参照。
 *
 * <p>「(playerUuid, jobId) の ring buffer + curve lookup」を管理する。
 * ログイン時に非同期でリポジトリから最新 window 件を読み込み、ring buffer を初期化する。
 * パイプライン実行時の read / update は main thread からのみ行われる想定で、
 * 内部状態はスレッドセーフではない Map を使う（main thread ロック相当）。
 */
public final class VarietyPenaltyEvaluator {

    private final Plugin plugin;
    private final ActionLogRepository actionLogRepo;
    private final AsyncExecutor asyncExecutor;

    /** (playerUuid, jobId) → ring buffer。 */
    private final Map<Key, VarietyRingBuffer> ringBuffers = new HashMap<>();
    /** jobId → curve lookup キャッシュ。 */
    private final Map<String, VarietyCurveLookup> curveCache = new HashMap<>();

    public VarietyPenaltyEvaluator(
            Plugin plugin,
            ActionLogRepository actionLogRepo,
            AsyncExecutor asyncExecutor
    ) {
        this.plugin = plugin;
        this.actionLogRepo = actionLogRepo;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * ペナルティを計算し、ring buffer に「今回のアクションキー」を記録する。
     * variety_penalty が無効な場合は multiplier = 1.0 を返し ring buffer にも触れない。
     *
     * <p>main thread から呼ばれる前提。
     */
    public Result evaluateAndRecord(UUID playerUuid, JobDefinition job, String actionKey) {
        VarietyPenaltyConfig config = job.varietyPenalty();
        if (config == null || !config.enabled()) {
            return Result.noPenalty();
        }
        Key key = new Key(playerUuid, job.id());
        VarietyRingBuffer buffer = ringBuffers.computeIfAbsent(key, k -> new VarietyRingBuffer(config.window()));
        // 今回のアクションを含めずに ratio を計算する（look-back 意味論）。
        double ratio = buffer.topRatio();
        double multiplier = curveFor(job.id(), config).lookup(ratio);
        buffer.record(actionKey);
        return new Result(multiplier, ratio, config.disclosedMessage(), config.hideNumbers(), buffer.size(), config.window());
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
            asyncExecutor.runOnMain(() -> {
                VarietyRingBuffer buffer = ringBuffers.computeIfAbsent(key, k -> new VarietyRingBuffer(window));
                buffer.initFromRecent(recent);
            });
        });
    }

    /** プレイヤー切断時に memory を解放する。 */
    public void unload(UUID playerUuid) {
        ringBuffers.keySet().removeIf(k -> k.playerUuid().equals(playerUuid));
    }

    /** 現時点で保持している buffer の snapshot。UI 用途。null なら未初期化。 */
    public Snapshot snapshot(UUID playerUuid, JobId jobId) {
        VarietyRingBuffer buf = ringBuffers.get(new Key(playerUuid, jobId));
        if (buf == null) return null;
        return new Snapshot(buf.size(), buf.capacity(), buf.topRatio(), buf.topKey());
    }

    private VarietyCurveLookup curveFor(JobId jobId, VarietyPenaltyConfig config) {
        return curveCache.computeIfAbsent(jobId.value(), k -> new VarietyCurveLookup(config.curve()));
    }

    /** curve キャッシュを破棄する。reload 時に呼ぶ想定。 */
    public void invalidateCurves() {
        curveCache.clear();
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
