package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.detection.DetectedAction;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionKey;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.RewardEntry;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * パイプライン実行中に stage が書き換えていく可変 state。
 *
 * <p>spec/04-reward-pipeline.md および docs/plan/class-structure.md 「pipeline」を参照。
 */
public final class PipelineContext {

    private final Player player;
    private final JobId jobId;
    private final JobDefinition jobDefinition;
    private final RewardEntry matchedEntry;
    private final ActionKey derivedKey;
    private final int amount;
    private final Instant occurredAt;

    private SourceFlags sourceFlags;

    private int baseReward;
    private int finalReward;
    private int netPaid;
    private boolean rareHit;

    /** true になった以降の Stage は「0 を維持」する。段階 3（自動化対策）で立てる。 */
    private boolean zeroLocked;

    private final List<String> zeroReasons = new ArrayList<>();

    public PipelineContext(DetectedAction action, JobDefinition job, Instant occurredAt) {
        this.player = action.player();
        this.jobId = action.matchedJobId();
        this.jobDefinition = job;
        this.matchedEntry = action.matchedEntry();
        this.derivedKey = action.derivedKey();
        this.amount = action.amount();
        this.sourceFlags = action.sourceFlags() == null ? SourceFlags.none() : action.sourceFlags();
        this.occurredAt = occurredAt;
    }

    public Player player() { return player; }
    public JobId jobId() { return jobId; }
    public JobDefinition jobDefinition() { return jobDefinition; }
    public RewardEntry matchedEntry() { return matchedEntry; }
    public ActionKey derivedKey() { return derivedKey; }
    public int amount() { return amount; }
    public Instant occurredAt() { return occurredAt; }

    public SourceFlags sourceFlags() { return sourceFlags; }
    public void setSourceFlags(SourceFlags flags) { this.sourceFlags = flags; }

    public int baseReward() { return baseReward; }
    public void setBaseReward(int v) { this.baseReward = v; }

    public int finalReward() { return finalReward; }
    public void setFinalReward(int v) { this.finalReward = v; }

    public int netPaid() { return netPaid; }
    public void setNetPaid(int v) { this.netPaid = v; }

    public boolean rareHit() { return rareHit; }
    public void setRareHit(boolean v) { this.rareHit = v; }

    public boolean zeroLocked() { return zeroLocked; }
    public void lockZero(String reason) {
        this.zeroLocked = true;
        this.finalReward = 0;
        this.netPaid = 0;
        if (reason != null) zeroReasons.add(reason);
    }

    public List<String> zeroReasons() { return List.copyOf(zeroReasons); }
}
