package me.f0reach.jobs.pipeline.stage;

import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.pipeline.PipelineContext;
import me.f0reach.jobs.pipeline.Stage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * 段階 12。advancement 経路の場合のみ、{@link AdvancementProgress#revokeCriteria(String)}
 * を呼んで同 advancement が再発火可能な状態に戻す。
 * spec/04-reward-pipeline.md 「revokeCriteria」を参照。
 *
 * <p>main thread 保証。revokeCriteria は Bukkit main thread からのみ安全に呼べる。
 * pipeline 全体が main thread で回るのでここでの追加スケジューリングは不要。
 */
public final class AdvancementRevokeStage implements Stage {

    private final Plugin plugin;

    public AdvancementRevokeStage(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Result execute(PipelineContext ctx) {
        if (!(ctx.matchedEntry().criteria() instanceof MatchCriteria.Advancement adv)) {
            return Result.CONTINUE;
        }
        NamespacedKey key = adv.advancement();
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) return Result.CONTINUE;

        AdvancementProgress progress = ctx.player().getAdvancementProgress(advancement);
        try {
            for (String criterion : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criterion);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "revokeCriteria failed for advancement " + key, e);
        }
        return Result.CONTINUE;
    }
}
