package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.specialty.SpecialtyService;

/**
 * 段階 2。listener 時と pipeline 実行時の間に専業が変わっていた稀ケースを潰す。
 */
public final class SpecialtyStage implements Stage {

    private final SpecialtyService specialtyService;

    public SpecialtyStage(SpecialtyService specialtyService) {
        this.specialtyService = specialtyService;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        var current = specialtyService.currentJob(ctx.player().getUniqueId());
        if (current.isEmpty()) return Result.HALT;
        if (!current.get().equals(ctx.jobId())) return Result.HALT;
        return Result.CONTINUE;
    }
}
