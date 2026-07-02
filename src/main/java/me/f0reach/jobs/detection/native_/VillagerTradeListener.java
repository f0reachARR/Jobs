package me.f0reach.jobs.detection.native_;

import me.f0reach.jobs.antiautomation.TradeRecorder;
import me.f0reach.jobs.detection.DetectionSubject;
import me.f0reach.jobs.detection.EventDispatcher;
import me.f0reach.jobs.detection.SourceFlags;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.matcher.MatchContext;
import me.f0reach.jobs.registry.JobRegistry;
import me.f0reach.jobs.specialty.SpecialtyService;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

import java.util.UUID;

/**
 * villager_traded: Merchant の result slot への click で取引成立時。
 * amount には取引個数を載せる。
 * Phase 7: 取引成立時に {@link TradeRecorder} で cooldown マーカーを書き、
 * {@link DetectionSubject} に villagerUuid / recipeIndex を載せる。
 */
public final class VillagerTradeListener implements Listener {

    private final EventDispatcher dispatcher;
    private final TradeRecorder tradeRecorder;
    private final SpecialtyService specialtyService;
    private final JobRegistry jobRegistry;

    public VillagerTradeListener(
            EventDispatcher dispatcher,
            TradeRecorder tradeRecorder,
            SpecialtyService specialtyService,
            JobRegistry jobRegistry
    ) {
        this.dispatcher = dispatcher;
        this.tradeRecorder = tradeRecorder;
        this.specialtyService = specialtyService;
        this.jobRegistry = jobRegistry;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.MERCHANT) return;
        // Merchant の result slot は 2
        if (event.getRawSlot() != 2) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MerchantInventory merchant = event.getInventory() instanceof MerchantInventory mi ? mi : null;
        UUID villagerUuid = merchant != null && merchant.getMerchant() instanceof AbstractVillager v
                ? v.getUniqueId() : null;
        int recipeIndex = merchant != null ? merchant.getSelectedRecipeIndex() : -1;

        writeTradeMarker(player.getUniqueId(), villagerUuid, recipeIndex);

        MatchContext ctx = MatchContext.builder()
                .item(result.getType().getKey())
                .amount(Math.max(1, result.getAmount()))
                .build();
        DetectionSubject subject = DetectionSubject.builder()
                .villagerUuid(villagerUuid)
                .recipeIndex(recipeIndex >= 0 ? recipeIndex : null)
                .build();
        dispatcher.dispatch(player, ActionType.VILLAGER_TRADED, ctx, SourceFlags.none(), subject);
    }

    private void writeTradeMarker(UUID playerUuid, UUID villagerUuid, int recipeIndex) {
        if (villagerUuid == null || recipeIndex < 0) return;
        JobDefinition job = specialtyService.currentJob(playerUuid)
                .flatMap(jobRegistry::get).orElse(null);
        if (job == null || job.antiAutomation() == null) return;
        AntiAutomationConfig.VillagerRepeatTrade vrt = job.antiAutomation().villagerRepeatTrade();
        if (vrt == null) return;
        tradeRecorder.recordTrade(villagerUuid, recipeIndex, vrt.cooldownSec());
    }
}
