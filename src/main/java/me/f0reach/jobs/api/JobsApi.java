package me.f0reach.jobs.api;

import me.f0reach.jobs.api.extension.JobRewardModifier;
import me.f0reach.jobs.api.extension.JobRewardSplitter;
import me.f0reach.jobs.api.query.ActionLogQueryService;
import me.f0reach.jobs.api.specialty.PlayerJobService;

/**
 * 外部プラグインへの公開 API 起点。
 * spec/06-public-api.md 「拡張点」および「ライフサイクル」を参照。
 *
 * <p>{@link me.f0reach.jobs.api.lifecycle.JobsPluginReadyEvent} で渡され、
 * 購読側は Modifier / Splitter の登録および Query サービスへのアクセスをここから取る。
 */
public interface JobsApi {

    /** 拡張 Modifier chain の登録簿。 */
    ExtensionRegistry<JobRewardModifier> getModifierRegistry();

    /** 拡張 Splitter chain の登録簿。 */
    ExtensionRegistry<JobRewardSplitter> getSplitterRegistry();

    /** 行動ログの集計クエリ。 */
    ActionLogQueryService getQueryService();

    /** プレイヤーの現在専業を取得・変更する API。 */
    PlayerJobService getPlayerJobService();

    /**
     * 登録簿の共通 interface。id 単位で register / unregister する。
     * 同じ id で 2 回目以降の register は上書き扱い (spec/06「同じ ID の Modifier/Splitter を
     * 後から差し替えできる」)。
     */
    interface ExtensionRegistry<T> {
        void register(T extension);
        void unregister(String id);
    }
}
