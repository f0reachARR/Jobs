package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.modifier.dailycap.DailyCapEvaluator;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;

/**
 * 段階 6。内蔵 Modifier (variety_penalty → daily_cap) を順に適用する。
 * spec/04-reward-pipeline.md 「内蔵 Modifier」を参照。
 *
 * <p>zeroLocked ならスキップして 0 を維持する。
 * variety の disclosed_message は /jobs status で参照するため、副作用として
 * {@link VarietyPenaltyEvaluator} 側の ring buffer に「今回のアクションキー」を記録する。
 * cap で削られた額は {@link PipelineContext#lockZero(String)} ではなく理由列に append するだけで、
 * finalReward を 0 まで下げても以降の RewardRoundingStage / EconomyTransferStage に流し続ける。
 */
public final class BuiltinModifierStage implements Stage {

    private final VarietyPenaltyEvaluator variety;
    private final DailyCapEvaluator dailyCap;

    public BuiltinModifierStage(VarietyPenaltyEvaluator variety, DailyCapEvaluator dailyCap) {
        this.variety = variety;
        this.dailyCap = dailyCap;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.zeroLocked()) return Result.CONTINUE;

        double reward = ctx.finalReward();

        VarietyPenaltyEvaluator.Result varietyResult = variety.evaluateAndRecord(
                ctx.player().getUniqueId(),
                ctx.jobDefinition(),
                ctx.derivedKey().value()
        );
        if (varietyResult.isPenalized()) {
            reward = reward * varietyResult.multiplier();
        }
        ctx.setFinalReward(reward);

        DailyCapEvaluator.Result capResult = dailyCap.evaluate(
                ctx.player().getUniqueId(),
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
                ctx.player().getUniqueId(),
                ctx.jobId().value(),
                ctx.finalReward()
        );

        return Result.CONTINUE;
    }
}
