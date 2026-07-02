package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.antiautomation.PlacementRecorder;
import me.f0reach.jobs.antiautomation.PlantedFlagWriter;
import me.f0reach.jobs.detection.DetectionSubject;
import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.matcher.MatchContext;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.specialty.SpecialtyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * block_placed: プレイヤーが置いた block を dispatch し、Phase 7 の
 * anti_automation マーキング (PlantedFlagWriter / PlacementRecorder) も行う。
 *
 * <p>マーキングは Player の現専業設定に依らず、常時 dry-run で書く方針も取れるが、
 * 「現専業のジョブが anti_automation を有効化している場合のみ書く」ことで、
 * 使わない KVS エントリの生成を抑える。
 * (spec/03-action-detection.md 「自動化対策のマーキング」の期待に沿う)
 */
public final class BlockPlaceListener implements Listener {

    private final EventDispatcher dispatcher;
    private final PlantedFlagWriter plantedFlagWriter;
    private final PlacementRecorder placementRecorder;
    private final SpecialtyService specialtyService;
    private final JobRegistry jobRegistry;

    public BlockPlaceListener(
            EventDispatcher dispatcher,
            PlantedFlagWriter plantedFlagWriter,
            PlacementRecorder placementRecorder,
            SpecialtyService specialtyService,
            JobRegistry jobRegistry
    ) {
        this.dispatcher = dispatcher;
        this.plantedFlagWriter = plantedFlagWriter;
        this.placementRecorder = placementRecorder;
        this.specialtyService = specialtyService;
        this.jobRegistry = jobRegistry;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        var player = event.getPlayer();
        var block = event.getBlockPlaced();

        writeMarkers(player.getUniqueId(), event);

        MatchContext ctx = MatchContext.builder()
                .block(block.getType().getKey())
                .amount(1)
                .build();
        DetectionSubject subject = DetectionSubject.builder()
                .block(block)
                .build();
        dispatcher.dispatch(player, ActionType.BLOCK_PLACED, ctx, SourceFlags.none(), subject);
    }

    private void writeMarkers(java.util.UUID playerUuid, BlockPlaceEvent event) {
        JobDefinition job = specialtyService.currentJob(playerUuid)
                .flatMap(jobRegistry::get).orElse(null);
        if (job == null) return;
        AntiAutomationConfig cfg = job.antiAutomation();
        if (cfg == null) return;

        if (cfg.unplantedCropHarvest() == AntiAutomationConfig.UnplantedCropHarvest.ZERO) {
            plantedFlagWriter.markPlanted(event.getBlockPlaced());
        }
        AntiAutomationConfig.RecentlyPlacedBreak rpb = cfg.recentlyPlacedBreak();
        if (rpb != null && rpb.enabled()) {
            placementRecorder.recordPlacement(event.getBlockPlaced(), rpb.windowSec());
        }
    }
}
