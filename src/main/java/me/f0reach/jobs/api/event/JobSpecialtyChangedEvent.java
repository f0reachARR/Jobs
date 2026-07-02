package me.f0reach.jobs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * プレイヤーが専業を選択または変更したときに発火する。
 * 初回選択（previousJobId が null）も同じイベントを使う。
 *
 * spec/06-public-api.md 「JobSpecialtyChangedEvent」を参照。
 */
public final class JobSpecialtyChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final @Nullable String previousJobId;
    private final String newJobId;
    private final Instant changedAt;

    public JobSpecialtyChangedEvent(
            Player player,
            @Nullable String previousJobId,
            String newJobId,
            Instant changedAt
    ) {
        this.player = player;
        this.previousJobId = previousJobId;
        this.newJobId = newJobId;
        this.changedAt = changedAt;
    }

    public Player getPlayer() { return player; }
    public @Nullable String getPreviousJobId() { return previousJobId; }
    public String getNewJobId() { return newJobId; }
    public Instant getChangedAt() { return changedAt; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
