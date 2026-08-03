package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.modifier.dailycap.DailyCapEvaluator;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 段階 6。内蔵 Modifier (variety_penalty → daily_cap) を順に適用する。
 * spec/04-reward-pipeline.md 「内蔵 Modifier」を参照。
 *
 * <p>zeroLocked ならスキップして 0 を維持する。
 * variety の disclosed_message は /jobs status で参照するため、副作用として
 * {@link VarietyPenaltyEvaluator} 側の ring buffer に「今回のアクションキー」を記録する。
 * cap で削られた額は {@link PipelineContext#lockZero(String)} ではなく理由列に append するだけで、
 * finalReward を 0 まで下げても以降の RewardRoundingStage / EconomyTransferStage に流し続ける。
 *
 * <p>daily_cap の日付は処理時刻ではなく {@link PipelineContext#occurredAt()} から求める。
 * ワーカーのキューが深いと、23:59 のアクションが日付をまたいだあとに処理され、
 * 翌日の枠へ計上されてしまう（docs/plan/async-reward-pipeline.md）。
 */
public final class BuiltinModifierStage implements Stage {

    private final VarietyPenaltyEvaluator variety;
    private final DailyCapEvaluator dailyCap;
    private final ZoneId zone;

    public BuiltinModifierStage(
            VarietyPenaltyEvaluator variety,
            DailyCapEvaluator dailyCap,
            ZoneId zone
    ) {
        this.variety = variety;
        this.dailyCap = dailyCap;
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public Affinity affinity() { return Affinity.WORKER; }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.zeroLocked()) return Result.CONTINUE;

        double reward = ctx.finalReward();

        // variety: 記録は継続し（バイパスを外した後に履歴が消えないよう）、
        // multiplier だけ 1.0 固定にする。
        VarietyPenaltyEvaluator.Result varietyResult = variety.evaluateAndRecord(
                ctx.playerUuid(),
                ctx.jobDefinition(),
                ctx.derivedKey().value()
        );
        if (varietyResult.isPenalized() && !ctx.bypassVarietyPenalty()) {
            reward = reward * varietyResult.multiplier();
        }
        ctx.setFinalReward(reward);

        // daily_cap: バイパス時は判定と累計 increment を丸ごとスキップして素通し。
        if (ctx.bypassDailyCap()) {
            return Result.CONTINUE;
        }

        LocalDate rewardDate = ctx.occurredAt().atZone(zone).toLocalDate();
        DailyCapEvaluator.Result capResult = dailyCap.evaluate(
                ctx.playerUuid(),
                rewardDate,
                ctx.jobId().value(),
                reward
        );
        ctx.setFinalReward(capResult.paidReward());
        if (capResult.trimmed() > 0.0) {
            // spec: 削った分は zeroReasons に「daily_cap_hit」として残す。
            // 0 lock はしない（丸め・log 書き込みは走らせる）。
            ctx.addZeroReason("daily_cap_hit");
        }

        // 支払確定後、cache 側の当日累計を increment する。
        // Splitter や Modifier で更に削られる可能性は Phase 8 で扱う。
        dailyCap.recordPaid(
                ctx.playerUuid(),
                rewardDate,
                ctx.jobId().value(),
                ctx.finalReward()
        );

        return Result.CONTINUE;
    }
}
