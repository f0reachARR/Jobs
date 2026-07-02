package me.f0reach.jobs.detection.advancement;

import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.matcher.MatchContext;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * Track B: PlayerAdvancementDoneEvent を受け、namespace が {@code jobs} の advancement のみを流す。
 *
 * <p>revokeCriteria の呼び出しは {@link me.f0reach.jobs.pipeline.stage.AdvancementRevokeStage}
 * が pipeline 段階 12 で行う。ここでは pipeline に advancementProgress を持ち込むための情報を
 * MatchContext.advancementKey に載せて渡す (Phase 9 では PipelineContext 側で
 * revoke するかは AdvancementDoneEvent の Advancement オブジェクトから
 * player.getAdvancementProgress() を再取得する)。
 */
public final class AdvancementListener implements Listener {

    /** Job プラグイン同梱の advancement namespace。 */
    public static final String NAMESPACE = "jobs";

    private final EventDispatcher dispatcher;

    public AdvancementListener(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        NamespacedKey key = event.getAdvancement().getKey();
        if (!NAMESPACE.equals(key.getNamespace())) return;

        MatchContext ctx = MatchContext.builder()
                .advancementKey(key)
                .amount(1)
                .build();
        dispatcher.dispatch(event.getPlayer(), ActionType.ADVANCEMENT, ctx, SourceFlags.none());
    }
}
