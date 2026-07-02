package me.f0reach.jobs.registry;

import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RepairSource;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionKeyDeriverTest {

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    private final ActionKeyDeriver deriver = new ActionKeyDeriver();

    @Test
    void singleEntityKilled() {
        var criteria = new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie")));
        assertEquals("kill:minecraft:zombie", deriver.derive(criteria).value());
    }

    @Test
    void listEntitySorted() {
        var criteria = new MatchCriteria.EntityKilled(new KeyMatcher.ListOf(List.of(
                NamespacedKey.minecraft("zombie"),
                NamespacedKey.minecraft("husk")
        )));
        assertEquals("kill:[minecraft:husk|minecraft:zombie]", deriver.derive(criteria).value());
    }

    @Test
    void tagEntity() {
        var criteria = new MatchCriteria.EntityKilled(new KeyMatcher.Tag(NamespacedKey.minecraft("undead")));
        assertEquals("kill:#minecraft:undead", deriver.derive(criteria).value());
    }

    @Test
    void blockBrokenViaTntUsesDedicatedPrefix() {
        var criteria = new MatchCriteria.BlockBroken(
                new KeyMatcher.Single(NamespacedKey.minecraft("stone")),
                false, true);
        assertEquals("break_tnt:minecraft:stone", deriver.derive(criteria).value());
    }

    @Test
    void repairSourceAffectsPrefix() {
        var item = new KeyMatcher.Single(NamespacedKey.minecraft("diamond_pickaxe"));
        assertEquals("repair_anvil:minecraft:diamond_pickaxe",
                deriver.derive(new MatchCriteria.ItemRepaired(item, RepairSource.ANVIL)).value());
        assertEquals("repair_mending:minecraft:diamond_pickaxe",
                deriver.derive(new MatchCriteria.ItemRepaired(item, RepairSource.MENDING)).value());
        assertEquals("repair:minecraft:diamond_pickaxe",
                deriver.derive(new MatchCriteria.ItemRepaired(item, null)).value());
    }

    @Test
    void fishTreasureIsFlat() {
        var criteria = new MatchCriteria.ItemFished(null, true);
        assertEquals("fish:treasure", deriver.derive(criteria).value());
    }

    @Test
    void advancementUsesFullKey() {
        var criteria = new MatchCriteria.Advancement(
                new NamespacedKey("jobs", "combat/charged_creeper_sword_kill"));
        assertEquals("adv:jobs:combat/charged_creeper_sword_kill", deriver.derive(criteria).value());
    }
}
