package me.f0reach.jobs.matcher;

import me.f0reach.jobs.domain.job.ConsumeCategory;
import me.f0reach.jobs.domain.job.Dimension;
import me.f0reach.jobs.domain.job.RepairSource;
import org.bukkit.NamespacedKey;

import java.util.Map;
import java.util.UUID;

/**
 * matcher に渡す 1 イベントぶんのランタイム値。
 * listener が event ごとに build する。ActionType ごとに埋める field が異なる。
 *
 * <p>spec/03-action-detection.md 「match の評価」で列挙される match フィールドと対応する。
 */
public record MatchContext(
        NamespacedKey entity,
        NamespacedKey block,
        NamespacedKey item,
        boolean cropMature,
        boolean viaTnt,
        boolean treasure,
        int amount,
        Map<NamespacedKey, Integer> enchantments,
        RepairSource repairSource,
        ConsumeCategory consumedCategory,
        NamespacedKey advancementKey,
        UUID tntPrimer,
        boolean spawnerOrigin,
        NamespacedKey potionType,
        Dimension dimension
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private NamespacedKey entity;
        private NamespacedKey block;
        private NamespacedKey item;
        private boolean cropMature;
        private boolean viaTnt;
        private boolean treasure;
        private int amount = 1;
        private Map<NamespacedKey, Integer> enchantments = Map.of();
        private RepairSource repairSource;
        private ConsumeCategory consumedCategory;
        private NamespacedKey advancementKey;
        private UUID tntPrimer;
        private boolean spawnerOrigin;
        private NamespacedKey potionType;
        private Dimension dimension;

        public Builder entity(NamespacedKey v) { this.entity = v; return this; }
        public Builder block(NamespacedKey v) { this.block = v; return this; }
        public Builder item(NamespacedKey v) { this.item = v; return this; }
        public Builder cropMature(boolean v) { this.cropMature = v; return this; }
        public Builder viaTnt(boolean v) { this.viaTnt = v; return this; }
        public Builder treasure(boolean v) { this.treasure = v; return this; }
        public Builder amount(int v) { this.amount = v; return this; }
        public Builder enchantments(Map<NamespacedKey, Integer> v) { this.enchantments = v == null ? Map.of() : Map.copyOf(v); return this; }
        public Builder repairSource(RepairSource v) { this.repairSource = v; return this; }
        public Builder consumedCategory(ConsumeCategory v) { this.consumedCategory = v; return this; }
        public Builder advancementKey(NamespacedKey v) { this.advancementKey = v; return this; }
        public Builder tntPrimer(UUID v) { this.tntPrimer = v; return this; }
        public Builder spawnerOrigin(boolean v) { this.spawnerOrigin = v; return this; }
        public Builder potionType(NamespacedKey v) { this.potionType = v; return this; }
        public Builder dimension(Dimension v) { this.dimension = v; return this; }

        public MatchContext build() {
            return new MatchContext(
                    entity, block, item, cropMature, viaTnt, treasure, amount,
                    enchantments, repairSource, consumedCategory, advancementKey,
                    tntPrimer, spawnerOrigin, potionType, dimension
            );
        }
    }
}
