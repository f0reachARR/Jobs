package me.f0reach.jobs.domain.job;

import java.util.Locale;

/**
 * 15 種類のアクション種別と派生キーのプレフィクス。
 * 詳細は spec/03-action-detection.md 「match の評価」の対応表を参照。
 */
public enum ActionType {
    ENTITY_KILLED("kill"),
    BLOCK_BROKEN("break"),
    BLOCK_PLACED("place"),
    ITEM_FISHED("fish"),
    ITEM_SMELTED("smelt"),
    ITEM_CRAFTED("craft"),
    ITEM_ENCHANTED("enchant"),
    ITEM_REPAIRED("repair"),
    ENTITY_BRED("breed"),
    ENTITY_TAMED("tame"),
    ENTITY_SHEARED("shear"),
    ITEM_CONSUMED("consume"),
    VILLAGER_TRADED("trade"),
    ITEM_BREWED("brew"),
    ADVANCEMENT("adv");

    private final String prefix;

    ActionType(String prefix) {
        this.prefix = prefix;
    }

    /** 派生キーのプレフィクス。ActionKeyDeriver で使う。 */
    public String prefix() {
        return prefix;
    }

    /** YAML の `on:` フィールドをパースする。 */
    public static ActionType fromYaml(String raw) {
        String norm = raw.toUpperCase(Locale.ROOT);
        try {
            return ActionType.valueOf(norm);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown action type: " + raw, e);
        }
    }
}
