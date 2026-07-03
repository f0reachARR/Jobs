package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.Permissions;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.specialty.SpecialtyService;

/**
 * 段階 2。listener 時と pipeline 実行時の間に専業が変わっていた稀ケースを潰す。
 *
 * <p>{@link Permissions#BYPASS_SPECIALTY} を持つプレイヤーは判定をスキップして通過させる
 * （spec/08-permissions.md）。専業未選択でも通過するため、以降のパイプラインが進む。
 */
public final class SpecialtyStage implements Stage {

    private final SpecialtyService specialtyService;

    public SpecialtyStage(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (ctx.player().hasPermission(Permissions.BYPASS_SPECIALTY)) return Result.CONTINUE;
        var current = specialtyService.currentJob(ctx.player().getUniqueId());
        if (current.isEmpty()) return Result.HALT;
        if (!current.get().equals(ctx.jobId())) return Result.HALT;
        return Result.CONTINUE;
    }
}
