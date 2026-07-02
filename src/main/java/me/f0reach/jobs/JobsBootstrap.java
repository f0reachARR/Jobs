package me.f0reach.jobs;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.f0reach.jobs.command.JobsCommands;
import org.jetbrains.annotations.NotNull;

/**
 * paper-plugin.yml から bootstrapper として呼ばれる。
 * ここでは {@link JobsCommands} のツリーを構築して registrar に登録するだけで、
 * 実行 handler の中で使う {@link JobsServices} は onEnable で {@link JobsCommands#bindServices}
 * を通じて差し込まれる。
 */
public final class JobsBootstrap implements PluginBootstrap {

    /** onEnable から取り出せるよう static field で共有する。 */
    private static JobsCommands COMMANDS;

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        JobsCommands commands = new JobsCommands();
        COMMANDS = commands;
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    commands.buildTree(),
                    "Jobs plugin root command"
            );
        });
    }

    public static JobsCommands getCommands() {
        return COMMANDS;
    }
}
