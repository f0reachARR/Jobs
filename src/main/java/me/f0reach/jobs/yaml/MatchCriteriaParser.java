package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.ConsumeCategory;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RepairSource;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import org.bukkit.NamespacedKey;

import java.util.Locale;
import java.util.Map;

/**
 * `on:` フィールドと match フィールド (entity/block/item/...) を組み合わせて
 * {@link MatchCriteria} を組み立てる。
 */
public final class MatchCriteriaParser {

    private final KeyMatcherParser keyMatcher = new KeyMatcherParser();

    public MatchCriteria parse(ActionType actionType, Map<?, ?> entry, String path) {
        return switch (actionType) {
            case ENTITY_KILLED -> new MatchCriteria.EntityKilled(requireMatcher(entry, "entity", path));
            case BLOCK_BROKEN -> new MatchCriteria.BlockBroken(
                    requireMatcher(entry, "block", path),
                    boolField(entry, "crop_mature"),
                    boolField(entry, "via_tnt")
            );
            case BLOCK_PLACED -> new MatchCriteria.BlockPlaced(requireMatcher(entry, "block", path));
            case ITEM_FISHED -> {
                boolean treasure = boolField(entry, "treasure");
                KeyMatcher item = optionalMatcher(entry, "item", path);
                yield new MatchCriteria.ItemFished(item, treasure);
            }
            case ITEM_SMELTED -> new MatchCriteria.ItemSmelted(requireMatcher(entry, "item", path));
            case ITEM_CRAFTED -> new MatchCriteria.ItemCrafted(requireMatcher(entry, "item", path));
            case ITEM_ENCHANTED -> new MatchCriteria.ItemEnchanted(
                    requireMatcher(entry, "item", path),
                    optionalKey(entry, "enchantment", path),
                    intField(entry, "level_min", 0)
            );
            case ITEM_REPAIRED -> new MatchCriteria.ItemRepaired(
                    requireMatcher(entry, "item", path),
                    parseRepairSource(entry.get("source"), path)
            );
            case ENTITY_BRED -> new MatchCriteria.EntityBred(requireMatcher(entry, "entity", path));
            case ENTITY_TAMED -> new MatchCriteria.EntityTamed(requireMatcher(entry, "entity", path));
            case ENTITY_SHEARED -> new MatchCriteria.EntitySheared(requireMatcher(entry, "entity", path));
            case ITEM_CONSUMED -> new MatchCriteria.ItemConsumed(
                    requireMatcher(entry, "item", path),
                    parseConsumeCategory(entry.get("category"), path)
            );
            case VILLAGER_TRADED -> new MatchCriteria.VillagerTraded(requireMatcher(entry, "item", path));
            case ITEM_BREWED -> new MatchCriteria.ItemBrewed(requireMatcher(entry, "item", path));
            case ADVANCEMENT -> {
                NamespacedKey key = optionalKey(entry, "advancement", path);
                if (key == null) throw new YamlParseException(path + ".advancement: required");
                yield new MatchCriteria.Advancement(key);
            }
        };
    }

    private KeyMatcher requireMatcher(Map<?, ?> entry, String field, String path) {
        Object raw = entry.get(field);
        if (raw == null) {
            throw new YamlParseException(path + "." + field + ": required");
        }
        return keyMatcher.parse(raw, path + "." + field);
    }

    private KeyMatcher optionalMatcher(Map<?, ?> entry, String field, String path) {
        Object raw = entry.get(field);
        if (raw == null) return null;
        return keyMatcher.parse(raw, path + "." + field);
    }

    private NamespacedKey optionalKey(Map<?, ?> entry, String field, String path) {
        Object raw = entry.get(field);
        if (raw == null) return null;
        if (!(raw instanceof String s)) {
            throw new YamlParseException(path + "." + field + ": expected string key");
        }
        NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
        if (key == null) {
            throw new YamlParseException(path + "." + field + ": invalid NamespacedKey '" + s + "'");
        }
        return key;
    }

    private boolean boolField(Map<?, ?> entry, String field) {
        Object raw = entry.get(field);
        if (raw instanceof Boolean b) return b;
        return false;
    }

    private int intField(Map<?, ?> entry, String field, int defaultValue) {
        Object raw = entry.get(field);
        if (raw instanceof Number n) return n.intValue();
        return defaultValue;
    }

    private RepairSource parseRepairSource(Object raw, String path) {
        if (raw == null) return null;
        if (raw instanceof String s) {
            try {
                return RepairSource.valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new YamlParseException(path + ".source: unknown '" + s + "'", e);
            }
        }
        throw new YamlParseException(path + ".source: expected string");
    }

    private ConsumeCategory parseConsumeCategory(Object raw, String path) {
        if (raw == null) return null;
        if (raw instanceof String s) {
            try {
                return ConsumeCategory.valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new YamlParseException(path + ".category: unknown '" + s + "'", e);
            }
        }
        throw new YamlParseException(path + ".category: expected string");
    }
}
