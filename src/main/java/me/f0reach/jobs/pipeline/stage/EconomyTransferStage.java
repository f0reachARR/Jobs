package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.economy.VaultEconomyAdapter;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import me.f0reach.jobs.pipeline.async.MainWorkQueue;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * 段階 10。Vault Economy に送金する。net_paid が 0 のときは skip。
 *
 * spec/04-reward-pipeline.md 「Economy へ送金」を参照。
 *
 * <p>既定ではワーカースレッド上で送金する。Economy プラグインがスレッドセーフでない環境では
 * {@code reward.async.economy_on_main: true} を立て、{@link MainWorkQueue} 経由で
 * main thread へ渡す（docs/plan/async-reward-pipeline.md）。
 * 投げ返す場合、送金の成否が確定するのは段階 11 の行動ログ書き込みより後になりうる。
 * 送金失敗は SEVERE ログに一本化し、行動ログへは反映しない。
 */
public final class EconomyTransferStage implements Stage {

    private final Plugin plugin;
    private final VaultEconomyAdapter economy;
    private final MainWorkQueue mainWorkQueue;
    private final boolean onMain;

    public EconomyTransferStage(
            Plugin plugin,
            VaultEconomyAdapter economy,
            MainWorkQueue mainWorkQueue,
            boolean onMain
    ) {
        this.plugin = plugin;
        this.economy = economy;
        this.mainWorkQueue = mainWorkQueue;
        this.onMain = onMain;
    }

    @Override
    public Affinity affinity() { return Affinity.WORKER; }

    @Override
    public Result execute(PipelineContext ctx) {
        // Splitter が netPaid を書き換えていない場合に finalReward で埋める。
        if (ctx.netPaid() == 0.0 && !ctx.zeroLocked()) {
            ctx.setNetPaid(ctx.finalReward());
        }
        double amount = ctx.netPaid();
        if (amount <= 0.0) return Result.CONTINUE;

        if (onMain) {
            mainWorkQueue.post(() -> deposit(ctx, amount));
        } else {
            deposit(ctx, amount);
        }
        return Result.CONTINUE;
    }

    /** Vault は OfflinePlayer を受け取るので、キュー滞在中にログアウトしていても送金できる。 */
    private void deposit(PipelineContext ctx, double amount) {
        try {
            boolean ok = economy.deposit(ctx.player(), amount);
            if (!ok) {
                plugin.getLogger().warning(
                        "Vault deposit failed for " + ctx.playerName() + " amount=" + amount
                );
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Vault deposit threw for " + ctx.playerName(), e);
        }
    }
}
