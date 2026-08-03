package me.f0reach.jobs.pipeline;

import me.f0reach.jobs.Permissions;
import me.f0reach.jobs.detection.DetectedAction;
import me.f0reach.jobs.detection.DetectionSubject;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionKey;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.RewardEntry;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * パイプライン実行中に stage が書き換えていく可変 state。
 *
 * <p>spec/04-reward-pipeline.md および docs/plan/class-structure.md 「pipeline」を参照。
 *
 * <p>prologue（段階 1 から 3 と 12）は main thread で走り、段階 4 以降は
 * {@code RewardWorker} 上で走る（docs/plan/async-reward-pipeline.md）。
 * この境界を越える前に {@link #detachBukkitRefs()} を呼び、同 tick 内でしか
 * 安全に触れない {@link DetectionSubject} の {@code Block} / {@code Entity} 参照を捨てる。
 *
 * <p>bypass 権限は prologue で解決して boolean で持つ。ワーカーから
 * {@link Player#hasPermission(String)} を呼ばずに済み、パイプライン全体で判定が一貫する。
 */
public final class PipelineContext {

    private final Player player;
    private final UUID playerUuid;
    private final String playerName;
    private final JobId jobId;
    private final JobDefinition jobDefinition;
    private final RewardEntry matchedEntry;
    private final ActionKey derivedKey;
    private final int amount;
    private final Instant occurredAt;

    private final boolean bypassSpecialty;
    private final boolean bypassAntiAutomation;
    private final boolean bypassVarietyPenalty;
    private final boolean bypassDailyCap;

    private SourceFlags sourceFlags;
    private DetectionSubject subject;

    private double baseReward;
    private double finalReward;
    private double netPaid;
    private boolean rareHit;

    /** true になった以降の Stage は「0 を維持」する。段階 3（自動化対策）で立てる。 */
    private boolean zeroLocked;

    /** 自動化対策に該当したときしか使わないので遅延生成する。 */
    private List<String> zeroReasons;

    public PipelineContext(DetectedAction action, JobDefinition job, Instant occurredAt) {
        this.player = action.player();
        this.playerUuid = player.getUniqueId();
        this.playerName = player.getName();
        this.jobId = action.matchedJobId();
        this.jobDefinition = job;
        this.matchedEntry = action.matchedEntry();
        this.derivedKey = action.derivedKey();
        this.amount = action.amount();
        this.sourceFlags = action.sourceFlags() == null ? SourceFlags.none() : action.sourceFlags();
        this.subject = action.subject() == null ? DetectionSubject.empty() : action.subject();
        this.occurredAt = occurredAt;
        // prologue（main thread）で 1 度だけ解決する。
        this.bypassSpecialty = player.hasPermission(Permissions.BYPASS_SPECIALTY);
        this.bypassAntiAutomation = player.hasPermission(Permissions.BYPASS_ANTI_AUTOMATION);
        this.bypassVarietyPenalty = player.hasPermission(Permissions.BYPASS_VARIETY_PENALTY);
        this.bypassDailyCap = player.hasPermission(Permissions.BYPASS_DAILY_CAP);
    }

    /**
     * ワーカーへ渡す前に呼ぶ。1 アクションごとに別物を掴む {@code Block} / {@code Entity}
     * 参照を捨て、段階 4 以降が同 tick 前提の Bukkit 状態に触れないことを保証する。
     *
     * <p>{@link #player()} の参照は残す。同一プレイヤーの複数タスクが同じ 1 個を共有するので
     * キューが深くても取り分は増えず、{@code Bukkit.getPlayer(UUID)} をワーカーから
     * 呼ばずに済む（スレッド安全性が保証されていない）。
     */
    public void detachBukkitRefs() {
        this.subject = DetectionSubject.empty();
    }

    /**
     * プレイヤー本体。prologue では常にオンライン。
     * ワーカー上ではキュー滞在中にログアウトしている可能性があるので {@link #isPlayerOnline()} で確認する。
     */
    public Player player() { return player; }

    public UUID playerUuid() { return playerUuid; }

    public String playerName() { return playerName; }

    public boolean isPlayerOnline() { return player.isOnline(); }

    public JobId jobId() { return jobId; }
    public JobDefinition jobDefinition() { return jobDefinition; }
    public RewardEntry matchedEntry() { return matchedEntry; }
    public ActionKey derivedKey() { return derivedKey; }
    public int amount() { return amount; }
    public Instant occurredAt() { return occurredAt; }

    public boolean bypassSpecialty() { return bypassSpecialty; }
    public boolean bypassAntiAutomation() { return bypassAntiAutomation; }
    public boolean bypassVarietyPenalty() { return bypassVarietyPenalty; }
    public boolean bypassDailyCap() { return bypassDailyCap; }

    public SourceFlags sourceFlags() { return sourceFlags; }
    public void setSourceFlags(SourceFlags flags) { this.sourceFlags = flags; }

    public DetectionSubject subject() { return subject; }

    public double baseReward() { return baseReward; }
    public void setBaseReward(double v) { this.baseReward = v; }

    public double finalReward() { return finalReward; }
    public void setFinalReward(double v) { this.finalReward = v; }

    public double netPaid() { return netPaid; }
    public void setNetPaid(double v) { this.netPaid = v; }

    public boolean rareHit() { return rareHit; }
    public void setRareHit(boolean v) { this.rareHit = v; }

    public boolean zeroLocked() { return zeroLocked; }
    public void lockZero(String reason) {
        this.zeroLocked = true;
        this.finalReward = 0.0;
        this.netPaid = 0.0;
        addZeroReason(reason);
    }

    /** 監査用に理由列だけ append する（0 lock は掛けない）。cap の部分削減などで使う。 */
    public void addZeroReason(String reason) {
        if (reason == null) return;
        if (zeroReasons == null) zeroReasons = new ArrayList<>(2);
        zeroReasons.add(reason);
    }

    public List<String> zeroReasons() {
        return zeroReasons == null ? List.of() : List.copyOf(zeroReasons);
    }
}
