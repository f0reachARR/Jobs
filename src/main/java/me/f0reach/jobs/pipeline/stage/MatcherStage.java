package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;

/**
 * 段階 1。listener 側で既に matcher が動いているため、この Stage は
 * 「matcher の結果を明示的に確定させる」役として残す（レビュー時に段階 1 の存在が読める）。
 */
public final class MatcherStage implements Stage {

    @Override
    public Result execute(PipelineContext ctx) {
        return ctx.matchedEntry() == null ? Result.HALT : Result.CONTINUE;
    }
}
