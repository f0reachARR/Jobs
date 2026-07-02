package me.f0reach.jobs.matcher;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import me.f0reach.jobs.registry.TagResolver;
import org.bukkit.NamespacedKey;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * rewards[] を上から評価し、first match wins で 1 件確定する。
 *
 * <p>spec/03-action-detection.md 「first match wins」と ADR-0004 を参照。
 */
public final class RewardMatcher {

    private final TagResolver tagResolver;

    public RewardMatcher(TagResolver tagResolver) {
        this.tagResolver = tagResolver;
    }

    public Optional<RewardEntry> firstMatch(JobDefinition job, ActionType actionType, MatchContext ctx) {
        for (RewardEntry entry : job.rewards()) {
            if (entry.actionType() != actionType) continue;
            if (matches(entry.criteria(), ctx)) return Optional.of(entry);
        }
        return Optional.empty();
    }

    /** 主に unit test 用にパッケージ可視で公開する。 */
    boolean matches(MatchCriteria criteria, MatchContext ctx) {
        return switch (criteria) {
            case MatchCriteria.EntityKilled c ->
                    matchesKey(c.entity(), ctx.entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.BlockBroken c -> {
                // via_tnt: criteria.viaTnt == ctx.viaTnt。criteria.viaTnt=false のとき、ctx.viaTnt=true は弾く。
                if (c.viaTnt() != ctx.viaTnt()) yield false;
                if (c.cropMature() && !ctx.cropMature()) yield false;
                yield matchesKey(c.block(), ctx.block(), TagResolver.Kind.BLOCKS);
            }
            case MatchCriteria.BlockPlaced c ->
                    matchesKey(c.block(), ctx.block(), TagResolver.Kind.BLOCKS);
            case MatchCriteria.ItemFished c -> {
                if (c.treasure() && !ctx.treasure()) yield false;
                if (c.item() == null) {
                    yield c.treasure() && ctx.treasure();
                }
                yield matchesKey(c.item(), ctx.item(), TagResolver.Kind.ITEMS);
            }
            case MatchCriteria.ItemSmelted c ->
                    matchesKey(c.item(), ctx.item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemCrafted c ->
                    matchesKey(c.item(), ctx.item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemEnchanted c -> {
                if (!matchesKey(c.item(), ctx.item(), TagResolver.Kind.ITEMS)) yield false;
                if (c.enchantment() == null) yield true;
                Integer level = ctx.enchantments() == null ? null : ctx.enchantments().get(c.enchantment());
                if (level == null) yield false;
                yield level >= c.levelMin();
            }
            case MatchCriteria.ItemRepaired c -> {
                if (!matchesKey(c.item(), ctx.item(), TagResolver.Kind.ITEMS)) yield false;
                if (c.source() == null) yield true;
                yield c.source() == ctx.repairSource();
            }
            case MatchCriteria.EntityBred c ->
                    matchesKey(c.entity(), ctx.entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.EntityTamed c ->
                    matchesKey(c.entity(), ctx.entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.EntitySheared c ->
                    matchesKey(c.entity(), ctx.entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.ItemConsumed c -> {
                if (!matchesKey(c.item(), ctx.item(), TagResolver.Kind.ITEMS)) yield false;
                if (c.category() == null) yield true;
                yield c.category() == ctx.consumedCategory();
            }
            case MatchCriteria.VillagerTraded c ->
                    matchesKey(c.item(), ctx.item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemBrewed c ->
                    matchesKey(c.item(), ctx.item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.Advancement c ->
                    Objects.equals(c.advancement(), ctx.advancementKey());
        };
    }

    private boolean matchesKey(KeyMatcher matcher, NamespacedKey target, TagResolver.Kind kind) {
        if (matcher == null || target == null) return false;
        java.util.function.Function<NamespacedKey, Set<NamespacedKey>> lookup = tagResolver.lookupFunction(kind);
        return matcher.matches(target, lookup);
    }
}
