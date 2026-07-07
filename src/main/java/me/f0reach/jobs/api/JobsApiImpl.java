package me.f0reach.jobs.api;

import me.f0reach.jobs.api.extension.JobRewardModifier;
import me.f0reach.jobs.api.extension.JobRewardSplitter;
import me.f0reach.jobs.api.query.ActionLogQueryService;
import me.f0reach.jobs.api.specialty.PlayerJobService;

/**
 * {@link JobsApi} の実装。JobsServices が組み立てて所持する。
 * JobsPluginReadyEvent の getApi() で公開する。
 */
public final class JobsApiImpl implements JobsApi {

    private final ExtensionRegistry<JobRewardModifier> modifierRegistry;
    private final ExtensionRegistry<JobRewardSplitter> splitterRegistry;
    private final ActionLogQueryService queryService;
    private final PlayerJobService playerJobService;

    public JobsApiImpl(
            ExtensionRegistry<JobRewardModifier> modifierRegistry,
            ExtensionRegistry<JobRewardSplitter> splitterRegistry,
            ActionLogQueryService queryService,
            PlayerJobService playerJobService
    ) {
        this.modifierRegistry = modifierRegistry;
        this.splitterRegistry = splitterRegistry;
        this.queryService = queryService;
        this.playerJobService = playerJobService;
    }

    @Override
    public ExtensionRegistry<JobRewardModifier> getModifierRegistry() { return modifierRegistry; }

    @Override
    public ExtensionRegistry<JobRewardSplitter> getSplitterRegistry() { return splitterRegistry; }

    @Override
    public ActionLogQueryService getQueryService() { return queryService; }

    @Override
    public PlayerJobService getPlayerJobService() { return playerJobService; }
}
