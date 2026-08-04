package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.config.PluginConfig;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;
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
    private final Supplier<PluginConfig.RewardConfig> config;

    /** 固定の設定で組む。テストなど reload を伴わない用途向け。 */
    public RewardRoundingStage(Plugin plugin, PluginConfig.RewardConfig config) {
        this(plugin, () -> config);
    }

    /**
     * config を参照して組む。stage の list は差し替えないので、
     * {@code /jobs reload} 後の decimals / rounding_mode は毎回読み直して反映する。
     */
    public RewardRoundingStage(Plugin plugin, Supplier<PluginConfig.RewardConfig> config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public Affinity affinity() { return Affinity.WORKER; }

    @Override
    public Result execute(PipelineContext ctx) {
        // 3 つの額を別々の設定で丸めないよう、1 回だけ読む。
        PluginConfig.RewardConfig cfg = config.get();
        try {
            ctx.setBaseReward(round(ctx.baseReward(), cfg));
            ctx.setFinalReward(round(ctx.finalReward(), cfg));
            ctx.setNetPaid(round(ctx.netPaid(), cfg));
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

    private double round(double value, PluginConfig.RewardConfig cfg) {
        if (value == 0.0) return 0.0;
        return BigDecimal.valueOf(value)
                .setScale(cfg.decimals(), cfg.roundingMode())
                .doubleValue();
    }

    /** UNNECESSARY 単体テスト向け getter。 */
    public RoundingMode roundingMode() {
        return config.get().roundingMode();
    }
}
