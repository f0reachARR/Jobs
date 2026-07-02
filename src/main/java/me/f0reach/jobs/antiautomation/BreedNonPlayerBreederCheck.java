package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.pipeline.PipelineContext;

/**
 * breed_non_player_breeder: EntityBreedEvent#getBreeder が Player 以外なら 0。
 * spec/04-reward-pipeline.md 「自動化対策」6 番目。
 *
 * <p>Listener 側で「Player 以外は捨てる」パスを外す必要がある (0 ログを残すため)。
 * 判定情報は {@link me.f0reach.jobs.detection.DetectionSubject#breederIsPlayer()} で受け取る。
 */
public final class BreedNonPlayerBreederCheck implements AntiAutomationCheck {

    public static final String REASON = "breed_non_player_breeder";

    @Override
    public boolean appliesTo(PipelineContext ctx, ActionType actionType) {
        if (actionType != ActionType.ENTITY_BRED) return false;
        AntiAutomationConfig cfg = ctx.jobDefinition().antiAutomation();
        return cfg != null && cfg.breedNonPlayerBreeder() == AntiAutomationConfig.BreedNonPlayerBreeder.ZERO;
    }

    @Override
    public String evaluate(PipelineContext ctx) {
        Boolean breederIsPlayer = ctx.subject().breederIsPlayer();
        if (breederIsPlayer == null) return null; // 情報が渡ってこないなら通過扱い
        return breederIsPlayer ? null : REASON;
    }
}
