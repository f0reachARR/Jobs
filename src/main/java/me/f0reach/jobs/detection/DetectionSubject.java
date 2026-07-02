package me.f0reach.jobs.detection;

import me.f0reach.jobs.antiautomation.ContainerKind;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.UUID;

/**
 * DetectedAction が pipeline に持ち込む Bukkit 側の任意コンテキスト。
 * AntiAutomation の各 check は、pipeline 側でこれを介して Bukkit refs を参照する
 * (listener で anti_automation の判定を先取りせず、pipeline 段階 3 に集約する
 * ため。docs/plan/class-structure.md 「detection.native_」を参照)。
 *
 * <p>すべての field が nullable。同 tick で完結するため、Entity / Block の
 * 生存期間内に pipeline は走り切る前提。
 */
public record DetectionSubject(
        Entity killedEntity,
        Block block,
        Block containerBlock,
        ContainerKind containerKind,
        UUID villagerUuid,
        Integer recipeIndex,
        Boolean breederIsPlayer
) {
    public static DetectionSubject empty() {
        return new DetectionSubject(null, null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Entity killedEntity;
        private Block block;
        private Block containerBlock;
        private ContainerKind containerKind;
        private UUID villagerUuid;
        private Integer recipeIndex;
        private Boolean breederIsPlayer;

        public Builder killedEntity(Entity v) { this.killedEntity = v; return this; }
        public Builder block(Block v) { this.block = v; return this; }
        public Builder containerBlock(Block v) { this.containerBlock = v; return this; }
        public Builder containerKind(ContainerKind v) { this.containerKind = v; return this; }
        public Builder villagerUuid(UUID v) { this.villagerUuid = v; return this; }
        public Builder recipeIndex(Integer v) { this.recipeIndex = v; return this; }
        public Builder breederIsPlayer(Boolean v) { this.breederIsPlayer = v; return this; }

        public DetectionSubject build() {
            return new DetectionSubject(
                    killedEntity, block, containerBlock, containerKind,
                    villagerUuid, recipeIndex, breederIsPlayer
            );
        }
    }
}
