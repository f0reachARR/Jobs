package me.f0reach.jobs.api.extension;

/**
 * 報酬パイプラインの段階 7 で適用される拡張 Modifier。
 * spec/06-public-api.md 「JobRewardModifier」および ADR-0012 を参照。
 *
 * <p>実装は Job プラグインの ExtensionModifierChain に register して使う。
 * 個別 Modifier が例外を投げた場合、その 1 件のみ skip される (chain は継続)。
 *
 * <p><b>スレッド契約</b>：{@link #modify(JobRewardContext)} は Jobs 専用の
 * ワーカースレッドから呼ばれる。main thread ではない。詳細は
 * {@link JobRewardContext} を参照。
 *
 * <p>ワーカーは 1 本しかないので、ここでブロッキング I/O を行うと
 * サーバ全体の報酬処理が詰まる。所要時間が
 * {@code reward.async.slow_extension_threshold_ms} を超えると Jobs が
 * WARNING を出す。
 */
public interface JobRewardModifier {

    ModifiedReward modify(JobRewardContext ctx);

    /** 小さいほど先に適用される。同値なら宣言順。 */
    int getPriority();

    /** unregister / 上書き register のキー。 */
    String getId();
}
