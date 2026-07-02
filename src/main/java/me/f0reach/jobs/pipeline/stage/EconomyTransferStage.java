package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.economy.VaultEconomyAdapter;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * 段階 9。Vault Economy に送金する。net_paid が 0 のときは skip。
 *
 * spec/04-reward-pipeline.md 「Economy へ送金」を参照。
 */
public final class EconomyTransferStage implements Stage {

    private final Plugin plugin;
    private final VaultEconomyAdapter economy;

    public EconomyTransferStage(Plugin plugin, VaultEconomyAdapter economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        // Phase 5 では Splitter が未実装のため netPaid=finalReward で埋める。
        // Phase 8 で SplitterStage が事前に netPaid を書き換える。
        if (ctx.netPaid() == 0 && !ctx.zeroLocked()) {
            ctx.setNetPaid(ctx.finalReward());
        }
        int amount = ctx.netPaid();
        if (amount <= 0) return Result.CONTINUE;
        try {
            boolean ok = economy.deposit(ctx.player(), amount);
            if (!ok) {
                plugin.getLogger().warning(
                        "Vault deposit failed for " + ctx.player().getName() + " amount=" + amount
                );
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Vault deposit threw for " + ctx.player().getName(), e);
        }
        return Result.CONTINUE;
    }
}
