package me.f0reach.jobs;

import me.f0reach.jobs.command.JobsCommands;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class JobsPlugin extends JavaPlugin {
    private JobsServices services;

    @Override
    public void onEnable() {
        try {
            services = new JobsServices(this);
            services.wire();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Failed to start Jobs plugin", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        JobsCommands commands = JobsBootstrap.getCommands();
        if (commands != null) {
            commands.bindServices(services);
        } else {
            getLogger().warning(
                    "JobsBootstrap was not invoked. Ensure paper-plugin.yml has bootstrapper set."
            );
        }

        // Registry.tags() は SERVER_LOAD 完了後に安定するため、1 tick 遅延で shadow detection を走らせる。
        Bukkit.getScheduler().runTask(this, () -> {
            if (services != null) {
                services.runShadowDetection();
            }
        });

        // 拡張プラグインの Modifier / Splitter 登録を受け付ける契機。
        // 全プラグインの enable 順序に依存しないよう、1 tick 遅らせて発火する。
        Bukkit.getScheduler().runTask(this, () -> {
            if (services != null) {
                services.fireReadyEvent();
            }
        });
    }

    @Override
    public void onDisable() {
        JobsCommands commands = JobsBootstrap.getCommands();
        if (commands != null) {
            commands.unbindServices();
        }
        if (services != null) {
            services.shutdown();
            services = null;
        }
    }

    public JobsServices services() {
        return services;
    }
}
