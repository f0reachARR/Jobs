package me.f0reach.jobs.registry;

import me.f0reach.jobs.domain.job.ActionKey;
import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.MatchCriteria;

/**
 * {@link MatchCriteria} から派生キー ({@link ActionKey}) を生成する。
 *
 * spec/03-action-detection.md 「match の評価」の対応表を実装で表現する。
 * OR をリストやタグで指定したエントリは 1 バケットに集約される。
 */
public final class ActionKeyDeriver {

    public ActionKey derive(MatchCriteria criteria) {
        return switch (criteria) {
            case MatchCriteria.EntityKilled c ->
                    key(ActionType.ENTITY_KILLED.prefix(), c.entity().toDerivedKey());
            case MatchCriteria.BlockBroken c -> {
                String prefix = c.viaTnt() ? "break_tnt" : ActionType.BLOCK_BROKEN.prefix();
                yield key(prefix, c.block().toDerivedKey());
            }
            case MatchCriteria.BlockPlaced c ->
                    key(ActionType.BLOCK_PLACED.prefix(), c.block().toDerivedKey());
            case MatchCriteria.ItemFished c -> {
                if (c.treasure()) yield new ActionKey("fish:treasure");
                yield key(ActionType.ITEM_FISHED.prefix(), c.item().toDerivedKey());
            }
            case MatchCriteria.ItemSmelted c ->
                    key(ActionType.ITEM_SMELTED.prefix(), c.item().toDerivedKey());
            case MatchCriteria.ItemCrafted c ->
                    key(ActionType.ITEM_CRAFTED.prefix(), c.item().toDerivedKey());
            case MatchCriteria.ItemEnchanted c ->
                    key(ActionType.ITEM_ENCHANTED.prefix(), c.item().toDerivedKey());
            case MatchCriteria.ItemRepaired c -> {
                String prefix = switch (c.source()) {
                    case null -> ActionType.ITEM_REPAIRED.prefix();
                    case ANVIL -> "repair_anvil";
                    case MENDING -> "repair_mending";
                };
                yield key(prefix, c.item().toDerivedKey());
            }
            case MatchCriteria.EntityBred c ->
                    key(ActionType.ENTITY_BRED.prefix(), c.entity().toDerivedKey());
            case MatchCriteria.EntityTamed c ->
                    key(ActionType.ENTITY_TAMED.prefix(), c.entity().toDerivedKey());
            case MatchCriteria.EntitySheared c ->
                    key(ActionType.ENTITY_SHEARED.prefix(), c.entity().toDerivedKey());
            case MatchCriteria.ItemConsumed c ->
                    key(ActionType.ITEM_CONSUMED.prefix(), c.item().toDerivedKey());
            case MatchCriteria.VillagerTraded c ->
                    key(ActionType.VILLAGER_TRADED.prefix(), c.item().toDerivedKey());
            case MatchCriteria.ItemBrewed c -> {
                String body = c.item().toDerivedKey();
                if (c.potion() != null) {
                    // potion 指定は variety_penalty のバケットを分ける。'+' は KeyMatcher の
                    // toDerivedKey で使われず衝突しないので、item と potion の区切りに使う。
                    body = body + "+" + c.potion().toDerivedKey();
                }
                yield key(ActionType.ITEM_BREWED.prefix(), body);
            }
            case MatchCriteria.Advancement c ->
                    key(ActionType.ADVANCEMENT.prefix(), c.advancement().toString());
        };
    }

    private ActionKey key(String prefix, String body) {
        return new ActionKey(prefix + ":" + body);
    }
}
