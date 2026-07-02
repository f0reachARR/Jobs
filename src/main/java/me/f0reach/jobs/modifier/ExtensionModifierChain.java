package me.f0reach.jobs.modifier;

import me.f0reach.jobs.api.JobsApi;
import me.f0reach.jobs.api.extension.JobRewardContext;
import me.f0reach.jobs.api.extension.JobRewardModifier;
import me.f0reach.jobs.api.extension.ModifiedReward;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 外部プラグイン提供の {@link JobRewardModifier} 群を管理し、
 * pipeline から呼ばれて chain 適用する。
 *
 * spec/04-reward-pipeline.md 「拡張 Modifier chain」および
 * spec/06-public-api.md 「JobRewardModifier」を参照。
 *
 * <p>個別 Modifier が例外を投げた場合はその 1 件を skip して chain を継続する。
 */
public final class ExtensionModifierChain implements JobsApi.ExtensionRegistry<JobRewardModifier> {

    private final Plugin plugin;
    private final Map<String, JobRewardModifier> byId = new ConcurrentHashMap<>();

    public ExtensionModifierChain(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(JobRewardModifier modifier) {
        if (modifier == null || modifier.getId() == null) {
            throw new IllegalArgumentException("modifier and id must be non-null");
        }
        byId.put(modifier.getId(), modifier);
    }

    @Override
    public void unregister(String id) {
        if (id == null) return;
        byId.remove(id);
    }

    /** 優先度昇順で全 Modifier を適用し、最終 reward を返す。 */
    public double apply(PipelineContext ctx) {
        double reward = ctx.finalReward();
        List<JobRewardModifier> ordered = byId.values().stream()
                .sorted(Comparator.comparingInt(JobRewardModifier::getPriority))
                .toList();
        for (JobRewardModifier mod : ordered) {
            JobRewardContext modCtx = new PipelineJobRewardContext(ctx, reward);
            try {
                ModifiedReward result = mod.modify(modCtx);
                if (result != null) reward = result.reward();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "JobRewardModifier '" + mod.getId() + "' threw; skipping", e);
            }
        }
        return reward;
    }
}
