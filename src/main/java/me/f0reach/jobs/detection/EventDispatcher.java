package me.f0reach.jobs.detection;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.matcher.MatchContext;
import me.f0reach.jobs.matcher.RewardMatcher;
import me.f0reach.jobs.pipeline.RewardPipeline;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.detection.DetectionSubject;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * listener と RewardPipeline の間を仲介する。
 *
 * <p>listener は event 情報から MatchContext を組み、EventDispatcher#dispatch を呼ぶ。
 * dispatcher は以下を実行する。
 * <ol>
 *   <li>プレイヤーの現専業を SpecialtyService から取得。未選択なら ADR-0002 に従って捨てる。</li>
 *   <li>RewardMatcher で first match wins エントリを取る。</li>
 *   <li>マッチしたら DetectedAction を組んで RewardPipeline#run に渡す。</li>
 * </ol>
 */
public final class EventDispatcher {

    private final SpecialtyService specialtyService;
    private final JobRegistry jobRegistry;
    private final RewardMatcher matcher;
    private final RewardPipeline pipeline;

    public EventDispatcher(
            SpecialtyService specialtyService,
            JobRegistry jobRegistry,
            RewardMatcher matcher,
            RewardPipeline pipeline
    ) {
        this.specialtyService = specialtyService;
        this.jobRegistry = jobRegistry;
        this.matcher = matcher;
        this.pipeline = pipeline;
    }

    public void dispatch(Player player, ActionType actionType, MatchContext ctx, SourceFlags flags) {
        dispatch(player, actionType, ctx, flags, DetectionSubject.empty());
    }

    public void dispatch(Player player, ActionType actionType, MatchContext ctx) {
        dispatch(player, actionType, ctx, SourceFlags.none(), DetectionSubject.empty());
    }

    public void dispatch(
            Player player,
            ActionType actionType,
            MatchContext ctx,
            SourceFlags flags,
            DetectionSubject subject
    ) {
        var currentJobIdOpt = specialtyService.currentJob(player.getUniqueId());
        if (currentJobIdOpt.isEmpty()) return;
        JobDefinition job = jobRegistry.get(currentJobIdOpt.get()).orElse(null);
        if (job == null) return;

        Optional<RewardEntry> matched = matcher.firstMatch(job, actionType, ctx);
        if (matched.isEmpty()) return;

        DetectedAction action = new DetectedAction(
                player,
                currentJobIdOpt.get(),
                matched.get(),
                matched.get().derivedKey(),
                ctx.amount(),
                flags == null ? SourceFlags.none() : flags,
                subject == null ? DetectionSubject.empty() : subject
        );
        pipeline.run(action);
    }
}
