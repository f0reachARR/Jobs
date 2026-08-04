package me.f0reach.jobs.smelting;

import org.bukkit.block.Block;

import java.util.UUID;

/**
 * ブロック 1 個を map の key にするための値型。
 *
 * <p>{@code Block} 実装の equals / hashCode に依存せず、ワールドと座標だけで同一性を決める。
 */
record BlockRef(UUID world, int x, int y, int z) {

    static BlockRef of(Block block) {
        return new BlockRef(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
