package me.f0reach.jobs.antiautomation;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

/**
 * BlockPlaceListener から呼ばれ、Ageable Block の BlockState PDC に
 * 「Player が植えた」フラグを書く。
 *
 * spec/04-reward-pipeline.md 「自動化対策 - unplanted_crop」および
 * class-structure.md 「antiautomation.PlantedFlagWriter」を参照。
 */
public final class PlantedFlagWriter {

    private final NamespacedKey key;

    /** UnplantedCropCheck と同じ key を共有する。 */
    public PlantedFlagWriter(NamespacedKey key) {
        this.key = key;
    }

    /** BlockPlaceEvent で呼ぶ。Ageable でない block は何もしない。 */
    public void markPlanted(Block block) {
        if (!(block.getBlockData() instanceof Ageable)) return;
        var state = block.getState();
        if (!(state instanceof PersistentDataHolder holder)) return;
        holder.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        state.update(false, false);
    }
}
