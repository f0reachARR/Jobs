package me.f0reach.jobs.antiautomation;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.pipeline.PipelineContext;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * unplanted_crop_harvest: Ageable の Block の PDC に「植えた」フラグが無ければ 0。
 * spec/04-reward-pipeline.md 「自動化対策」2 番目。
 *
 * <p>フラグの書き込みは {@link PlantedFlagWriter} が BlockPlaceListener 経由で行う。
 * 判定対象は Ageable Block のみ (Ageable でない block は対象外で通過)。
 */
public final class UnplantedCropCheck implements AntiAutomationCheck {

    public static final String REASON = "unplanted_crop_harvest";
    private static final String FLAG_KEY = "planted_by_player";

    private final NamespacedKey key;

    public UnplantedCropCheck(Plugin plugin) {
        this.key = new NamespacedKey(plugin, FLAG_KEY);
    }

    @Override
    public boolean appliesTo(PipelineContext ctx, ActionType actionType) {
        if (actionType != ActionType.BLOCK_BROKEN) return false;
        AntiAutomationConfig cfg = ctx.jobDefinition().antiAutomation();
        return cfg != null && cfg.unplantedCropHarvest() == AntiAutomationConfig.UnplantedCropHarvest.ZERO;
    }

    @Override
    public String evaluate(PipelineContext ctx) {
        Block block = ctx.subject().block();
        if (block == null) return null;
        if (!(block.getBlockData() instanceof Ageable)) return null; // Ageable でなければ対象外
        return readPlantedFlag(block) ? null : REASON;
    }

    /** Paper の BlockState PDC 経由でフラグを読む。無ければ false。 */
    private boolean readPlantedFlag(Block block) {
        var state = block.getState();
        if (!(state instanceof org.bukkit.persistence.PersistentDataHolder holder)) return false;
        Byte b = holder.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return b != null && b != 0;
    }

    /** PlantedFlagWriter から書き込みで使う共有 key。 */
    public NamespacedKey key() {
        return key;
    }
}
