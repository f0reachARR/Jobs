package me.f0reach.jobs.pipeline;

/**
 * パイプラインの 1 段階。全て main thread で同期実行される。
 * async に載せたい処理は Stage の中で AsyncExecutor に投げる (threading.md)。
 */
public interface Stage {

    /** Stage 実行結果。CONTINUE で次段階へ、HALT で以降を打ち切って処理終了。 */
    enum Result { CONTINUE, HALT }

    Result execute(PipelineContext ctx);
}
