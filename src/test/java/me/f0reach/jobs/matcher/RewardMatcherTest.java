package me.f0reach.jobs.matcher;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RewardAmount;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import me.f0reach.jobs.registry.ActionKeyDeriver;
import me.f0reach.jobs.registry.TagResolver;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardMatcherTest {

    @BeforeAll
    static void setUp() { MockBukkit.mock(); }

    @AfterAll
    static void tearDown() { MockBukkit.unmock(); }

    private final ActionKeyDeriver keyDeriver = new ActionKeyDeriver();
    private final TagResolver tagResolver = new TagResolver();
    private final RewardMatcher matcher = new RewardMatcher(tagResolver);

    private JobDefinition job(List<RewardEntry> entries) {
        return new JobDefinition(
                new JobId("combat"),
                "Combat",
                NamespacedKey.minecraft("iron_sword"),
                entries,
                VarietyPenaltyConfig.disabled(),
                AntiAutomationConfig.empty()
        );
    }

    private RewardEntry entry(MatchCriteria criteria, int reward) {
        return new RewardEntry(
                criteria.actionType(),
                criteria,
                new RewardAmount.Fixed(reward),
                null,
                keyDeriver.derive(criteria)
        );
    }

    @Test
    void entityKilledSingleMatch() {
        var e = entry(new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie"))), 5);
        MatchContext ctx = MatchContext.builder().entity(NamespacedKey.minecraft("zombie")).build();
        Optional<RewardEntry> result = matcher.firstMatch(job(List.of(e)), ActionType.ENTITY_KILLED, ctx);
        assertTrue(result.isPresent());
        assertEquals("kill:minecraft:zombie", result.get().derivedKey().value());
    }

    @Test
    void entityKilledNonMatchReturnsEmpty() {
        var e = entry(new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie"))), 5);
        MatchContext ctx = MatchContext.builder().entity(NamespacedKey.minecraft("skeleton")).build();
        assertFalse(matcher.firstMatch(job(List.of(e)), ActionType.ENTITY_KILLED, ctx).isPresent());
    }

    @Test
    void listMatchesAnyElement() {
        var e = entry(new MatchCriteria.EntityKilled(new KeyMatcher.ListOf(List.of(
                NamespacedKey.minecraft("zombie"), NamespacedKey.minecraft("husk")
        ))), 3);
        MatchContext ctx = MatchContext.builder().entity(NamespacedKey.minecraft("husk")).build();
        assertTrue(matcher.firstMatch(job(List.of(e)), ActionType.ENTITY_KILLED, ctx).isPresent());
    }

    @Test
    void firstMatchWinsPicksTopEntry() {
        var top = entry(new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie"))), 5);
        var bottom = entry(new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie"))), 99);
        MatchContext ctx = MatchContext.builder().entity(NamespacedKey.minecraft("zombie")).build();
        var result = matcher.firstMatch(job(List.of(top, bottom)), ActionType.ENTITY_KILLED, ctx);
        assertTrue(result.isPresent());
        assertEquals(5, ((RewardAmount.Fixed) result.get().rewardAmount()).value());
    }

    @Test
    void blockBrokenViaTntFlagMustMatch() {
        var e = entry(new MatchCriteria.BlockBroken(
                new KeyMatcher.Single(NamespacedKey.minecraft("stone")), false, true), 1);
        // via_tnt=true エントリは、ctx.viaTnt=true のみマッチ。
        MatchContext viaTnt = MatchContext.builder().block(NamespacedKey.minecraft("stone")).viaTnt(true).build();
        MatchContext handBroken = MatchContext.builder().block(NamespacedKey.minecraft("stone")).build();
        assertTrue(matcher.firstMatch(job(List.of(e)), ActionType.BLOCK_BROKEN, viaTnt).isPresent());
        assertFalse(matcher.firstMatch(job(List.of(e)), ActionType.BLOCK_BROKEN, handBroken).isPresent());
    }

    @Test
    void blockBrokenNonTntCriteriaExcludesTntSource() {
        var e = entry(new MatchCriteria.BlockBroken(
                new KeyMatcher.Single(NamespacedKey.minecraft("stone")), false, false), 1);
        MatchContext viaTnt = MatchContext.builder().block(NamespacedKey.minecraft("stone")).viaTnt(true).build();
        assertFalse(matcher.firstMatch(job(List.of(e)), ActionType.BLOCK_BROKEN, viaTnt).isPresent());
    }

    @Test
    void enchantedRequiresLevelMinAndEnchantment() {
        var e = entry(new MatchCriteria.ItemEnchanted(
                new KeyMatcher.Single(NamespacedKey.minecraft("diamond_sword")),
                NamespacedKey.minecraft("sharpness"),
                3
        ), 10);
        MatchContext lvl4 = MatchContext.builder()
                .item(NamespacedKey.minecraft("diamond_sword"))
                .enchantments(java.util.Map.of(NamespacedKey.minecraft("sharpness"), 4))
                .build();
        MatchContext lvl2 = MatchContext.builder()
                .item(NamespacedKey.minecraft("diamond_sword"))
                .enchantments(java.util.Map.of(NamespacedKey.minecraft("sharpness"), 2))
                .build();
        assertTrue(matcher.firstMatch(job(List.of(e)), ActionType.ITEM_ENCHANTED, lvl4).isPresent());
        assertFalse(matcher.firstMatch(job(List.of(e)), ActionType.ITEM_ENCHANTED, lvl2).isPresent());
    }

    @Test
    void fishTreasureFiltersByFlag() {
        var e = entry(new MatchCriteria.ItemFished(null, true), 100);
        MatchContext t = MatchContext.builder().item(NamespacedKey.minecraft("nautilus_shell")).treasure(true).build();
        MatchContext normal = MatchContext.builder().item(NamespacedKey.minecraft("cod")).treasure(false).build();
        assertTrue(matcher.firstMatch(job(List.of(e)), ActionType.ITEM_FISHED, t).isPresent());
        assertFalse(matcher.firstMatch(job(List.of(e)), ActionType.ITEM_FISHED, normal).isPresent());
    }
}
