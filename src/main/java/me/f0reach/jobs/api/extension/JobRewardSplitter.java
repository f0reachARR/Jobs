package me.f0reach.jobs.api.extension;

/**
 * 報酬パイプラインの段階 8 で適用される拡張 Splitter。
 * spec/06-public-api.md 「JobRewardSplitter」および ADR-0012 を参照。
 *
 * <p>宣言順に適用される。2 つ目以降は「前 Splitter で差し引いた残額」を見て計算する。
 * 個別 Splitter が例外を投げた場合、その 1 件のみ skip される (chain は継続)。
 */
public interface JobRewardSplitter {

    Split split(JobRewardSplitContext ctx);

    /** 小さいほど先に適用される。同値なら宣言順。 */
    int getPriority();

    /** unregister / 上書き register のキー。 */
    String getId();
}
