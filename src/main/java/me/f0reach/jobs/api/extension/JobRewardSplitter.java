package me.f0reach.jobs.api.extension;

/**
 * 報酬パイプラインの段階 8 で適用される拡張 Splitter。
 * spec/06-public-api.md 「JobRewardSplitter」および ADR-0012 を参照。
 *
 * <p>宣言順に適用される。2 つ目以降は「前 Splitter で差し引いた残額」を見て計算する。
 * 個別 Splitter が例外を投げた場合、その 1 件のみ skip される (chain は継続)。
 *
 * <p><b>スレッド契約</b>：{@link #split(JobRewardSplitContext)} は Jobs 専用の
 * ワーカースレッドから呼ばれる。main thread ではない。詳細は
 * {@link JobRewardContext} を参照。
 *
 * <p>ワーカーは 1 本しかないので、ここでブロッキング I/O を行うと
 * サーバ全体の報酬処理が詰まる。所要時間が
 * {@code reward.async.slow_extension_threshold_ms} を超えると Jobs が
 * WARNING を出す。
 */
public interface JobRewardSplitter {

    Split split(JobRewardSplitContext ctx);

    /** 小さいほど先に適用される。同値なら宣言順。 */
    int getPriority();

    /** unregister / 上書き register のキー。 */
    String getId();
}
