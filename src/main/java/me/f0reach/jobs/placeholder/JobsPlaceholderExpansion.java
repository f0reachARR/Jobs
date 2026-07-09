package me.f0reach.jobs.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.f0reach.jobs.api.specialty.PlayerJobService;
import me.f0reach.jobs.registry.JobRegistry;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * PlaceholderAPI 拡張。プラグイン内蔵型（internal expansion）として同 jar から登録する。
 *
 * <p>提供する placeholder:
 * <ul>
 *   <li>{@code %jobs_current_id%} - 現在の職業 id。未選択・オフラインなら空文字。</li>
 *   <li>{@code %jobs_current_name%} - 現在の職業 display_name。解決不能なら空文字。</li>
 *   <li>{@code %jobs_has_job%} - 選択済みなら {@code "true"}、それ以外 {@code "false"}。</li>
 * </ul>
 *
 * <p>PAPI 未導入環境ではこのクラスを一切参照しない
 * ({@link me.f0reach.jobs.JobsPlugin} で存在チェックしてから load する)。
 */
public final class JobsPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final JobPlaceholderResolver resolver;

    public JobsPlaceholderExpansion(
            JavaPlugin plugin,
            PlayerJobService playerJobService,
            JobRegistry jobRegistry
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resolver = new JobPlaceholderResolver(playerJobService, jobRegistry);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "jobs";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** {@code /papi reload} で unregister されないよう persist する。 */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        if (player == null) return null;
        return resolver.resolve(player.getUniqueId(), params);
    }
}
