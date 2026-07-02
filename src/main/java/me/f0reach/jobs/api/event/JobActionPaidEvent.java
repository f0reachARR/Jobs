package me.f0reach.jobs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * 報酬パイプラインの最終段（spec/04-reward-pipeline.md 段階 10）で発火する。
 * 報酬が確定し、Economy への送金も完了したあと、非同期に fire される。
 *
 * spec/06-public-api.md 「JobActionPaidEvent」を参照。
 */
public final class JobActionPaidEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String jobId;
    private final String actionKey;
    private final int baseReward;
    private final int finalReward;
    private final int netPaid;
    private final boolean rareHit;
    private final int amount;
    private final Instant occurredAt;

    public JobActionPaidEvent(
            Player player,
            String jobId,
            String actionKey,
            int baseReward,
            int finalReward,
            int netPaid,
            boolean rareHit,
            int amount,
            Instant occurredAt
    ) {
        super(true); // async
        this.player = player;
        this.jobId = jobId;
        this.actionKey = actionKey;
        this.baseReward = baseReward;
        this.finalReward = finalReward;
        this.netPaid = netPaid;
        this.rareHit = rareHit;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }

    public Player getPlayer() { return player; }
    public String getJobId() { return jobId; }
    public String getActionKey() { return actionKey; }
    public int getBaseReward() { return baseReward; }
    public int getFinalReward() { return finalReward; }
    public int getNetPaid() { return netPaid; }
    public boolean isRareHit() { return rareHit; }
    public int getAmount() { return amount; }
    public Instant getOccurredAt() { return occurredAt; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
