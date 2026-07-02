package me.f0reach.jobs.api.query;

/**
 * 行動ログの絞り込み条件。
 * spec/06-public-api.md 「ActionLogQueryService」の型定義に対応する。
 *
 * <p>この型は公開 API と永続化層の双方から参照されるため、
 * api.query.* に物理的に置き、persistence.* からは import して使う（class-structure.md）。
 *
 * @param jobId           ジョブ ID (nullable)
 * @param actionKey       完全一致するアクションキー (nullable)
 * @param actionKeyPrefix 前方一致プレフィクス。"break:" など (nullable)
 */
public record ActionFilter(
        String jobId,
        String actionKey,
        String actionKeyPrefix
) {
    /** 何も絞り込まない場合の便利ファクトリ。 */
    public static ActionFilter none() {
        return new ActionFilter(null, null, null);
    }
}
