package me.f0reach.jobs.modifier.dailycap;

import me.f0reach.jobs.config.PluginConfig;

import java.util.UUID;

/**
 * 日次キャップを評価する。spec/04-reward-pipeline.md 「daily_cap」を参照。
 *
 * <p>{@link DailyTotalCache} から当日累計を取り、報酬を {@code amount - total} で切り詰める。
 * 既に上限に達しているなら 0。まだ余裕があれば余裕分だけ支払う。
 *
 * <p>スコープに応じて総額 or ジョブ別を参照する。
 * 「削った分」を計上する副作用は {@link BuiltinModifierStage} 側の処理でまとめる。
 */
public final class DailyCapEvaluator {

    private final DailyTotalView cache;
    private final PluginConfig.DailyCapConfig config;

    public DailyCapEvaluator(DailyTotalView cache, PluginConfig.DailyCapConfig config) {
        this.cache = cache;
        this.config = config;
    }

    public PluginConfig.DailyCapConfig.Scope scope() {
        return config.scope();
    }

    public double amount() {
        return config.amount();
    }

    /**
     * 現在の累計と提示された報酬から、実際に支払う額を返す。
     *
     * @param playerUuid    プレイヤー UUID
     * @param jobId         ジョブ ID (scope=PER_JOB 時のみ利用)
     * @param proposed      これから支払おうとしている報酬 (variety_penalty 適用済み)
     * @return {@link Result} 支払額と削減量、上限到達フラグ
     */
    public Result evaluate(UUID playerUuid, String jobId, double proposed) {
        double cap = config.amount();
        if (cap <= 0) {
            // amount<=0 は「無効化」相当として扱う（YAML 側の意図を尊重）。
            return new Result(proposed, 0.0, false, 0.0, cap);
        }
        double already = switch (config.scope()) {
            case TOTAL -> cache.todayTotal(playerUuid);
            case PER_JOB -> cache.todayForJob(playerUuid, jobId);
        };
        if (already >= cap) {
            return new Result(0.0, proposed, true, already, cap);
        }
        double remaining = cap - already;
        if (proposed <= remaining) {
            return new Result(proposed, 0.0, false, already, cap);
        }
        double paid = Math.max(0.0, remaining);
        double trimmed = proposed - paid;
        return new Result(paid, trimmed, paid <= 0.0, already, cap);
    }

    /** 支払確定後、cache を increment する（副作用）。 */
    public void recordPaid(UUID playerUuid, String jobId, double paid) {
        cache.add(playerUuid, jobId, paid);
    }

    /**
     * 評価結果。
     *
     * @param paidReward   支払額 (proposed - trimmed)
     * @param trimmed      キャップ超過で削られた額
     * @param capHit       このアクション後にキャップに達した / 既に達している場合 true
     * @param totalBefore  評価時点での当日累計 (このアクションを含まない)
     * @param cap          設定上の上限
     */
    public record Result(
            double paidReward,
            double trimmed,
            boolean capHit,
            double totalBefore,
            double cap
    ) {}
}
