package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.pipeline.PipelineContext;

/**
 * 自動化対策の 1 個の判定単位。
 * spec/04-reward-pipeline.md 「自動化対策」および class-structure.md 「antiautomation」を参照。
 *
 * <p>{@link AntiAutomationCoordinator} が有効チェックの順に呼び出す。
 * 「無効化対象ではない」場合は null を返す。無効化が発火するときは
 * {@link PipelineContext#zeroReasons()} に載せる短い識別子を返す。
 */
public interface AntiAutomationCheck {

    /** 判定対象の action_type。合致しない action は appliesTo=false でスキップされる。 */
    boolean appliesTo(PipelineContext ctx, ActionType actionType);

    /**
     * 実際の判定。0 lock すべきなら理由文字列 (spec 04 の「zeroReasons」列挙値相当) を返す。
     * 通過なら null。
     */
    String evaluate(PipelineContext ctx);
}
