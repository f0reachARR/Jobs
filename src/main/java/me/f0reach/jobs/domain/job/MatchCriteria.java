package me.f0reach.jobs.domain.job;

import me.f0reach.jobs.domain.matcher.KeyMatcher;
import org.bukkit.NamespacedKey;

import java.util.Objects;

/**
 * rewards エントリの match 条件を表す sealed hierarchy。
 * 各 ActionType に対応する record を持つ。
 *
 * 詳細は spec/02-yaml-schema.md 「rewards エントリ / match フィールド」を参照。
 */
public sealed interface MatchCriteria {

    /** 対応するアクション種別。 */
    ActionType actionType();

    record EntityKilled(KeyMatcher entity) implements MatchCriteria {
        public EntityKilled {
            Objects.requireNonNull(entity, "entity");
        }

        @Override
        public ActionType actionType() { return ActionType.ENTITY_KILLED; }
    }

    record BlockBroken(
            KeyMatcher block,
            boolean cropMature,
            boolean viaTnt
    ) implements MatchCriteria {
        public BlockBroken {
            Objects.requireNonNull(block, "block");
        }

        @Override
        public ActionType actionType() { return ActionType.BLOCK_BROKEN; }
    }

    record BlockPlaced(KeyMatcher block) implements MatchCriteria {
        public BlockPlaced {
            Objects.requireNonNull(block, "block");
        }

        @Override
        public ActionType actionType() { return ActionType.BLOCK_PLACED; }
    }

    record ItemFished(KeyMatcher item, boolean treasure) implements MatchCriteria {
        public ItemFished {
            // item は treasure 指定時に省略可能。treasure=false のときは item 必須。
            if (item == null && !treasure) {
                throw new IllegalArgumentException("item_fished requires item or treasure=true");
            }
        }

        @Override
        public ActionType actionType() { return ActionType.ITEM_FISHED; }
    }

    record ItemSmelted(KeyMatcher item) implements MatchCriteria {
        public ItemSmelted {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public ActionType actionType() { return ActionType.ITEM_SMELTED; }
    }

    record ItemCrafted(KeyMatcher item) implements MatchCriteria {
        public ItemCrafted {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public ActionType actionType() { return ActionType.ITEM_CRAFTED; }
    }

    record ItemEnchanted(
            KeyMatcher item,
            NamespacedKey enchantment,
            int levelMin
    ) implements MatchCriteria {
        public ItemEnchanted {
            Objects.requireNonNull(item, "item");
            if (levelMin < 0) throw new IllegalArgumentException("level_min must be >= 0");
        }

        @Override
        public ActionType actionType() { return ActionType.ITEM_ENCHANTED; }
    }

    record ItemRepaired(KeyMatcher item, RepairSource source) implements MatchCriteria {
        public ItemRepaired {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public ActionType actionType() { return ActionType.ITEM_REPAIRED; }
    }

    record EntityBred(KeyMatcher entity) implements MatchCriteria {
        public EntityBred {
            Objects.requireNonNull(entity, "entity");
        }

        @Override
        public ActionType actionType() { return ActionType.ENTITY_BRED; }
    }

    record EntityTamed(KeyMatcher entity) implements MatchCriteria {
        public EntityTamed {
            Objects.requireNonNull(entity, "entity");
        }

        @Override
        public ActionType actionType() { return ActionType.ENTITY_TAMED; }
    }

    record EntitySheared(KeyMatcher entity) implements MatchCriteria {
        public EntitySheared {
            Objects.requireNonNull(entity, "entity");
        }

        @Override
        public ActionType actionType() { return ActionType.ENTITY_SHEARED; }
    }

    record ItemConsumed(KeyMatcher item, ConsumeCategory category) implements MatchCriteria {
        public ItemConsumed {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public ActionType actionType() { return ActionType.ITEM_CONSUMED; }
    }

    record VillagerTraded(KeyMatcher item) implements MatchCriteria {
        public VillagerTraded {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public ActionType actionType() { return ActionType.VILLAGER_TRADED; }
    }

    record ItemBrewed(KeyMatcher item) implements MatchCriteria {
        public ItemBrewed {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public ActionType actionType() { return ActionType.ITEM_BREWED; }
    }

    record Advancement(NamespacedKey advancement) implements MatchCriteria {
        public Advancement {
            Objects.requireNonNull(advancement, "advancement");
        }

        @Override
        public ActionType actionType() { return ActionType.ADVANCEMENT; }
    }
}
