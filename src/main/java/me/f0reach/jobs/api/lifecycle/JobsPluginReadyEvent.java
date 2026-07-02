package me.f0reach.jobs.api.lifecycle;

import me.f0reach.jobs.api.JobsApi;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Job プラグインが起動を終え、拡張点 (JobRewardModifier / JobRewardSplitter) の
 * 登録を受け付ける準備が整ったことを通知する Bukkit Event。
 *
 * <p>spec/06-public-api.md 「ライフサイクル」の JOB_PLUGIN_READY に相当する。
 * Paper の {@code LifecycleEventType} と揃えたくなるところだが、依存を軽くするため
 * 通常の Bukkit event として発火する。
 *
 * <p>購読側は {@link JobsApi#getModifierRegistry()} などのアクセサから registry を取り、
 * 自分の実装を register する。
 */
public final class JobsPluginReadyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final JobsApi api;

    public JobsPluginReadyEvent(JobsApi api) {
        this.api = api;
    }

    public JobsApi getApi() {
        return api;
    }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
