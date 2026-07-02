package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;

/**
 * 段階 9。config の decimals と rounding_mode に従い、
 * baseReward / finalReward / netPaid を同じ桁数・同じ方式で丸める。
 *
 * <p>spec/04-reward-pipeline.md 「丸め」および ADR-0019 を参照。
 * Modifier / Splitter は double のまま計算し、丸めはここに一本化する。
 */
public final class RewardRoundingStage implements Stage {

    private final Plugin plugin;
    private final PluginConfig.RewardConfig config;

    public RewardRoundingStage(Plugin plugin, PluginConfig.RewardConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        try {
            ctx.setBaseReward(round(ctx.baseReward()));
            ctx.setFinalReward(round(ctx.finalReward()));
            ctx.setNetPaid(round(ctx.netPaid()));
        } catch (ArithmeticException e) {
            // rounding_mode: UNNECESSARY で端数があった場合。0 化して WARNING。
            plugin.getLogger().log(Level.WARNING,
                    "reward rounding failed with UNNECESSARY (base=" + ctx.baseReward()
                            + ", final=" + ctx.finalReward() + ", net=" + ctx.netPaid() + ")", e);
            ctx.setBaseReward(0.0);
            ctx.setFinalReward(0.0);
            ctx.setNetPaid(0.0);
        }
        return Result.CONTINUE;
    }

    private double round(double value) {
        if (value == 0.0) return 0.0;
        return BigDecimal.valueOf(value)
                .setScale(config.decimals(), config.roundingMode())
                .doubleValue();
    }

    /** UNNECESSARY 単体テスト向け getter。 */
    public RoundingMode roundingMode() {
        return config.roundingMode();
    }
}
