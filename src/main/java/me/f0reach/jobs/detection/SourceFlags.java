package me.f0reach.jobs.detection;

import java.util.UUID;

/**
 * DetectedAction / PipelineContext が持ち回るフラグ集合。
 * 自動化対策 (Phase 7) や TNT 起爆判定で使う。
 *
 * <p>Phase 5 では tntPrimer と viaTnt のみ埋まる。それ以外は Phase 7 以降で使う。
 */
public record SourceFlags(
        boolean viaTnt,
        UUID tntPrimer,
        boolean spawnerOrigin,
        boolean viaRecentlyPlaced,
        boolean viaAutoFed,
        boolean viaRepeatTrade,
        boolean viaNonPlayerBreeder
) {
    public static SourceFlags none() {
        return new SourceFlags(false, null, false, false, false, false, false);
    }

    public SourceFlags withViaRecentlyPlaced(boolean v) {
        return new SourceFlags(viaTnt, tntPrimer, spawnerOrigin, v, viaAutoFed, viaRepeatTrade, viaNonPlayerBreeder);
    }

    public SourceFlags withViaAutoFed(boolean v) {
        return new SourceFlags(viaTnt, tntPrimer, spawnerOrigin, viaRecentlyPlaced, v, viaRepeatTrade, viaNonPlayerBreeder);
    }
}
