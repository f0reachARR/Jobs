package me.f0reach.jobs.listener;

import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.modifier.dailycap.DailyTotalCache;
import me.f0reach.jobs.modifier.variety.VarietyPenaltyEvaluator;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.specialty.SpecialtyService;
import me.f0reach.jobs.ui.SpecialtyListDialog;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * プレイヤーログイン時に SpecialtyService / 内蔵 Modifier のキャッシュを warm し、
 * 未選択なら SpecialtyListDialog を SELECT モードで開く。
 *
 * spec/07-ui.md 「専業一覧ダイアログ」および Phase 6 の
 * VarietyPenaltyEvaluator / DailyTotalCache warmup を参照。
 */
public final class PlayerJoinListener implements Listener {

    private final SpecialtyService specialtyService;
    private final SpecialtyListDialog listDialog;
    private final VarietyPenaltyEvaluator varietyPenaltyEvaluator;
    private final DailyTotalCache dailyTotalCache;
    private final JobRegistry jobRegistry;
    private final boolean showSelectDialogOnJoin;

    public PlayerJoinListener(
            SpecialtyService specialtyService,
            SpecialtyListDialog listDialog,
            VarietyPenaltyEvaluator varietyPenaltyEvaluator,
            DailyTotalCache dailyTotalCache,
            JobRegistry jobRegistry,
            boolean showSelectDialogOnJoin
    ) {
        this.specialtyService = specialtyService;
        this.listDialog = listDialog;
        this.varietyPenaltyEvaluator = varietyPenaltyEvaluator;
        this.dailyTotalCache = dailyTotalCache;
        this.jobRegistry = jobRegistry;
        this.showSelectDialogOnJoin = showSelectDialogOnJoin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        specialtyService.loadPlayer(uuid);
        dailyTotalCache.warmup(uuid);
        specialtyService.currentJob(uuid).ifPresent(jobId -> warmupVariety(uuid, jobId));

        if (showSelectDialogOnJoin && specialtyService.isFirstTime(uuid)) {
            // 少し遅延させないと Dialog がまだ届かない場合がある。
            listDialog.open(player, SpecialtyListDialog.Mode.SELECT);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        specialtyService.unloadPlayer(uuid);
        varietyPenaltyEvaluator.unload(uuid);
        dailyTotalCache.unload(uuid);
    }

    private void warmupVariety(UUID uuid, JobId jobId) {
        JobDefinition def = jobRegistry.get(jobId).orElse(null);
        if (def == null) return;
        if (def.varietyPenalty() == null || !def.varietyPenalty().enabled()) return;
        varietyPenaltyEvaluator.warmup(uuid, jobId, def.varietyPenalty().window());
    }
}
