package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Level;

/**
 * AntiAutomation の複数 check を順に呼び、最初に 0 判定した理由を返す。
 * spec/04-reward-pipeline.md 「自動化対策」の判定順を実装する。
 *
 * <p>個別 check が例外を投げた場合は保守的に「通過」扱い (無効化しない) にする
 * (spec/04 「自動化対策：判定例外時は対策を『無効』とみなして通過させる」)。
 */
public final class AntiAutomationCoordinator {

    private final Plugin plugin;
    private final List<AntiAutomationCheck> checks;

    public AntiAutomationCoordinator(Plugin plugin, List<AntiAutomationCheck> checks) {
        this.plugin = plugin;
        this.checks = List.copyOf(checks);
    }

    /** 有効な check を順に呼び、最初に 0 判定した理由を返す。無ければ null。 */
    public String firstZero(PipelineContext ctx, ActionType actionType) {
        for (AntiAutomationCheck check : checks) {
            try {
                if (!check.appliesTo(ctx, actionType)) continue;
                String reason = check.evaluate(ctx);
                if (reason != null) return reason;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "AntiAutomation check " + check.getClass().getSimpleName() + " threw", e);
            }
        }
        return null;
    }
}
