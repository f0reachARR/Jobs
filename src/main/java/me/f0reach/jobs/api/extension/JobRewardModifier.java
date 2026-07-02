package me.f0reach.jobs.api.extension;

/**
 * 報酬パイプラインの段階 7 で適用される拡張 Modifier。
 * spec/06-public-api.md 「JobRewardModifier」および ADR-0012 を参照。
 *
 * <p>実装は Job プラグインの ExtensionModifierChain に register して使う。
 * 個別 Modifier が例外を投げた場合、その 1 件のみ skip される (chain は継続)。
 */
public interface JobRewardModifier {

    ModifiedReward modify(JobRewardContext ctx);

    /** 小さいほど先に適用される。同値なら宣言順。 */
    int getPriority();

    /** unregister / 上書き register のキー。 */
    String getId();
}
