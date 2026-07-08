package me.f0reach.jobs.registry;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 起動時に rewards[] を走査し、上位エントリが下位エントリのマッチ集合を完全に覆う
 * shadow を検出する（ADR-0004）。警告のみで拒否はしない。
 */
public final class ShadowDetector {

    private final TagResolver tagResolver;

    public ShadowDetector(TagResolver tagResolver) {
        this.tagResolver = tagResolver;
    }

    public record ShadowWarning(String jobId, int higherIndex, int lowerIndex, String reason) {}

    public List<ShadowWarning> detect(JobDefinition job) {
        List<ShadowWarning> warnings = new ArrayList<>();
        List<RewardEntry> entries = job.rewards();
        for (int lower = 1; lower < entries.size(); lower++) {
            RewardEntry lowerEntry = entries.get(lower);
            for (int higher = 0; higher < lower; higher++) {
                RewardEntry higherEntry = entries.get(higher);
                if (higherEntry.actionType() != lowerEntry.actionType()) continue;
                if (covers(higherEntry.criteria(), lowerEntry.criteria())) {
                    warnings.add(new ShadowWarning(
                            job.id().value(),
                            higher, lower,
                            "rewards[" + higher + "] covers rewards[" + lower + "]"
                    ));
                    break;
                }
            }
        }
        return warnings;
    }

    /** 上位 criteria が下位 criteria のマッチ集合を包含するか。 */
    private boolean covers(MatchCriteria higher, MatchCriteria lower) {
        if (higher.getClass() != lower.getClass()) return false;
        return switch (higher) {
            case MatchCriteria.EntityKilled h -> {
                MatchCriteria.EntityKilled l = (MatchCriteria.EntityKilled) lower;
                // higher が dimension を絞っている場合、lower も同じかそれより狭い集合でないと覆えない。
                // higher が empty (any) のとき、lower の dimension は無視して entity のみで判定する。
                if (!h.dimensions().isEmpty()) {
                    if (l.dimensions().isEmpty()) yield false;
                    if (!h.dimensions().containsAll(l.dimensions())) yield false;
                }
                yield coversKey(h.entity(), l.entity(), TagResolver.Kind.ENTITY_TYPES);
            }
            case MatchCriteria.BlockBroken h -> {
                MatchCriteria.BlockBroken l = (MatchCriteria.BlockBroken) lower;
                if (h.viaTnt() != l.viaTnt()) yield false;
                // crop_mature: higher が true のとき lower も true でないと覆えない。
                if (h.cropMature() && !l.cropMature()) yield false;
                yield coversKey(h.block(), l.block(), TagResolver.Kind.BLOCKS);
            }
            case MatchCriteria.BlockPlaced h ->
                    coversKey(h.block(), ((MatchCriteria.BlockPlaced) lower).block(), TagResolver.Kind.BLOCKS);
            case MatchCriteria.ItemFished h -> {
                MatchCriteria.ItemFished l = (MatchCriteria.ItemFished) lower;
                if (h.treasure() != l.treasure()) yield false;
                if (h.item() == null) yield l.item() == null; // treasure のみ vs treasure のみ
                if (l.item() == null) yield false;
                yield coversKey(h.item(), l.item(), TagResolver.Kind.ITEMS);
            }
            case MatchCriteria.ItemSmelted h ->
                    coversKey(h.item(), ((MatchCriteria.ItemSmelted) lower).item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemCrafted h ->
                    coversKey(h.item(), ((MatchCriteria.ItemCrafted) lower).item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemEnchanted h -> {
                MatchCriteria.ItemEnchanted l = (MatchCriteria.ItemEnchanted) lower;
                if (h.enchantment() != null && !Objects.equals(h.enchantment(), l.enchantment())) yield false;
                if (h.levelMin() > l.levelMin()) yield false;
                yield coversKey(h.item(), l.item(), TagResolver.Kind.ITEMS);
            }
            case MatchCriteria.ItemRepaired h -> {
                MatchCriteria.ItemRepaired l = (MatchCriteria.ItemRepaired) lower;
                // higher が指定なし (両方 accept) なら、lower の source を包含。
                if (h.source() != null && !Objects.equals(h.source(), l.source())) yield false;
                yield coversKey(h.item(), l.item(), TagResolver.Kind.ITEMS);
            }
            case MatchCriteria.EntityBred h ->
                    coversKey(h.entity(), ((MatchCriteria.EntityBred) lower).entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.EntityTamed h ->
                    coversKey(h.entity(), ((MatchCriteria.EntityTamed) lower).entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.EntitySheared h ->
                    coversKey(h.entity(), ((MatchCriteria.EntitySheared) lower).entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.ItemConsumed h -> {
                MatchCriteria.ItemConsumed l = (MatchCriteria.ItemConsumed) lower;
                if (h.category() != null && !Objects.equals(h.category(), l.category())) yield false;
                yield coversKey(h.item(), l.item(), TagResolver.Kind.ITEMS);
            }
            case MatchCriteria.VillagerTraded h ->
                    coversKey(h.item(), ((MatchCriteria.VillagerTraded) lower).item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemBrewed h -> {
                MatchCriteria.ItemBrewed l = (MatchCriteria.ItemBrewed) lower;
                if (h.potion() != null) {
                    if (l.potion() == null) yield false;
                    if (!coversPotion(h.potion(), l.potion())) yield false;
                }
                yield coversKey(h.item(), l.item(), TagResolver.Kind.ITEMS);
            }
            case MatchCriteria.Advancement h ->
                    Objects.equals(h.advancement(), ((MatchCriteria.Advancement) lower).advancement());
        };
    }

    /**
     * higher の集合が lower の集合を包含するか。
     * tag が絡む場合は TagResolver 経由で展開して集合比較する。
     */
    private boolean coversKey(KeyMatcher higher, KeyMatcher lower, TagResolver.Kind kind) {
        Set<NamespacedKey> hset = expand(higher, kind);
        Set<NamespacedKey> lset = expand(lower, kind);
        if (hset.isEmpty()) return false;
        return hset.containsAll(lset);
    }

    // PotionType には TagResolver.Kind を用意しないため、Single / ListOf のみで包含判定する。
    // Tag は parser 側で禁止済みだが、来た場合は空集合扱いで安全側 (包含しない) に倒す。
    private boolean coversPotion(KeyMatcher higher, KeyMatcher lower) {
        Set<NamespacedKey> hset = potionSet(higher);
        Set<NamespacedKey> lset = potionSet(lower);
        if (hset.isEmpty()) return false;
        return hset.containsAll(lset);
    }

    private Set<NamespacedKey> potionSet(KeyMatcher matcher) {
        return switch (matcher) {
            case KeyMatcher.Single s -> Set.of(s.key());
            case KeyMatcher.ListOf l -> Set.copyOf(l.keys());
            case KeyMatcher.Tag ignored -> Set.of();
        };
    }

    private Set<NamespacedKey> expand(KeyMatcher matcher, TagResolver.Kind kind) {
        Function<NamespacedKey, Set<NamespacedKey>> lookup = tagResolver.lookupFunction(kind);
        return switch (matcher) {
            case KeyMatcher.Single s -> Set.of(s.key());
            case KeyMatcher.ListOf l -> Set.copyOf(l.keys());
            case KeyMatcher.Tag t -> {
                Set<NamespacedKey> resolved = lookup.apply(t.tag());
                yield resolved == null ? Set.of() : resolved;
            }
        };
    }
}
