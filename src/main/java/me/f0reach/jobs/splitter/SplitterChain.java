package me.f0reach.jobs.splitter;

import me.f0reach.jobs.api.JobsApi;
import me.f0reach.jobs.api.extension.JobRewardSplitter;
import me.f0reach.jobs.api.extension.Split;
import me.f0reach.jobs.modifier.PipelineJobRewardContext;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 外部プラグイン提供の {@link JobRewardSplitter} 群を管理し、
 * pipeline から呼ばれて chain 適用する。
 *
 * spec/04-reward-pipeline.md 「Splitter chain」および
 * spec/06-public-api.md 「JobRewardSplitter」を参照。
 *
 * <p>個別 Splitter が例外を投げた場合はその 1 件を skip する。
 */
public final class SplitterChain implements JobsApi.ExtensionRegistry<JobRewardSplitter> {

    private final Plugin plugin;
    private final Map<String, JobRewardSplitter> byId = new ConcurrentHashMap<>();

    public SplitterChain(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(JobRewardSplitter splitter) {
        if (splitter == null || splitter.getId() == null) {
            throw new IllegalArgumentException("splitter and id must be non-null");
        }
        byId.put(splitter.getId(), splitter);
    }

    @Override
    public void unregister(String id) {
        if (id == null) return;
        byId.remove(id);
    }

    /**
     * chain を宣言順 (priority 昇順) に適用し、netPaid を計算して返す。
     * finalReward は変更しない。
     */
    public double applyAndComputeNet(PipelineContext ctx) {
        double net = ctx.finalReward();
        double afterModifiers = ctx.finalReward();
        List<JobRewardSplitter> ordered = byId.values().stream()
                .sorted(Comparator.comparingInt(JobRewardSplitter::getPriority))
                .toList();
        for (JobRewardSplitter s : ordered) {
            try {
                Split split = s.split(new PipelineJobRewardContext(ctx, net, afterModifiers));
                if (split == null) continue;
                double deducted = Math.max(0.0, split.deductedFromPlayer());
                net = Math.max(0.0, net - deducted);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING,
                        "JobRewardSplitter '" + s.getId() + "' threw; skipping", e);
            }
        }
        return net;
    }
}
