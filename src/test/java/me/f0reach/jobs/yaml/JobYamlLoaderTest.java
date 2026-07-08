package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.Dimension;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RewardAmount;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import me.f0reach.jobs.registry.ActionKeyDeriver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobYamlLoaderTest {

  @BeforeAll
  static void setUp() {
    MockBukkit.mock();
  }

  @AfterAll
  static void tearDown() {
    MockBukkit.unmock();
  }

  private JobYamlLoader newLoader() {
    return new JobYamlLoader(new ActionKeyDeriver());
  }

  @Test
  void loadsCombatSample(@TempDir Path dir) throws IOException {
    Path yml = dir.resolve("combat.yml");
    Files.writeString(yml, """
        id: combat
        display_name: "Combat"
        description: "戦って稼ぐ"
        icon: minecraft:iron_sword

        rewards:
          - on: entity_killed
            entity: minecraft:zombie
            reward: 5

          - on: entity_killed
            entity: [zombie, husk]
            reward: 3

          - on: entity_killed
            entity: "#minecraft:undead"
            reward: 2

          - on: block_broken
            block: minecraft:diamond_ore
            reward: { min: 3, max: 8 }

          - on: entity_killed
            entity: minecraft:skeleton
            reward: 5
            rare:
              chance: 0.0005
              reward: 100000
              announce: "<player> gold!"
        """, StandardCharsets.UTF_8);

    JobYamlLoader.LoadResult result = newLoader().loadDirectory(dir.toFile());
    assertFalse(result.errors().hasErrors(), () -> "errors: " + result.errors().entries());
    assertEquals(1, result.jobs().size());

    JobDefinition job = result.jobs().get(0);
    assertEquals("combat", job.id().value());
    assertEquals("Combat", job.displayName());
    assertEquals("戦って稼ぐ", job.description());
    assertEquals("minecraft:iron_sword", job.icon().toString());
    assertEquals(5, job.rewards().size());

    // 1 entry: single entity
    var first = job.rewards().get(0);
    assertEquals(ActionType.ENTITY_KILLED, first.actionType());
    assertInstanceOf(MatchCriteria.EntityKilled.class, first.criteria());
    assertInstanceOf(KeyMatcher.Single.class, ((MatchCriteria.EntityKilled) first.criteria()).entity());
    assertInstanceOf(RewardAmount.Fixed.class, first.rewardAmount());
    assertEquals("kill:minecraft:zombie", first.derivedKey().value());

    // 2 entry: list
    var second = job.rewards().get(1);
    assertInstanceOf(KeyMatcher.ListOf.class, ((MatchCriteria.EntityKilled) second.criteria()).entity());
    assertEquals("kill:[minecraft:husk|minecraft:zombie]", second.derivedKey().value());

    // 3 entry: tag
    var third = job.rewards().get(2);
    assertInstanceOf(KeyMatcher.Tag.class, ((MatchCriteria.EntityKilled) third.criteria()).entity());
    assertEquals("kill:#minecraft:undead", third.derivedKey().value());

    // 4 entry: block_broken range
    var fourth = job.rewards().get(3);
    assertEquals(ActionType.BLOCK_BROKEN, fourth.actionType());
    assertInstanceOf(RewardAmount.Range.class, fourth.rewardAmount());

    // 5 entry: rare
    var fifth = job.rewards().get(4);
    assertNotNull(fifth.rareBonus());
    assertEquals(0.0005, fifth.rareBonus().chance());
    assertEquals("<player> gold!", fifth.rareBonus().announceMessage());
  }

  @Test
  void descriptionIsOptional(@TempDir Path dir) throws IOException {
    Path yml = dir.resolve("nodesc.yml");
    Files.writeString(yml, """
        id: nodesc
        display_name: "NoDesc"
        icon: minecraft:stone
        """, StandardCharsets.UTF_8);
    JobYamlLoader.LoadResult result = newLoader().loadDirectory(dir.toFile());
    assertFalse(result.errors().hasErrors());
    assertNull(result.jobs().get(0).description());
  }

  @Test
  void collectsErrorForInvalidId(@TempDir Path dir) throws IOException {
    Path yml = dir.resolve("bad.yml");
    Files.writeString(yml, """
        id: Bad-Id
        display_name: "x"
        icon: minecraft:stone
        """, StandardCharsets.UTF_8);
    JobYamlLoader.LoadResult result = newLoader().loadDirectory(dir.toFile());
    assertTrue(result.errors().hasErrors());
    assertTrue(result.jobs().isEmpty());
  }

  @Test
  void parsesItemBrewedWithOptionalPotion(@TempDir Path dir) throws IOException {
    Path yml = dir.resolve("brew.yml");
    Files.writeString(yml, """
        id: brew
        display_name: "Brew"
        icon: minecraft:brewing_stand

        rewards:
          - on: item_brewed
            item: minecraft:potion
            reward: 8
          - on: item_brewed
            item: minecraft:splash_potion
            potion: minecraft:strong_healing
            reward: 20
          - on: item_brewed
            item: minecraft:lingering_potion
            potion: [minecraft:healing, minecraft:strong_healing]
            reward: 15
        """, StandardCharsets.UTF_8);

    JobYamlLoader.LoadResult result = newLoader().loadDirectory(dir.toFile());
    assertFalse(result.errors().hasErrors(), () -> "errors: " + result.errors().entries());
    JobDefinition job = result.jobs().get(0);
    assertEquals(3, job.rewards().size());

    MatchCriteria.ItemBrewed noPotion = (MatchCriteria.ItemBrewed) job.rewards().get(0).criteria();
    assertNull(noPotion.potion());

    MatchCriteria.ItemBrewed single = (MatchCriteria.ItemBrewed) job.rewards().get(1).criteria();
    assertInstanceOf(KeyMatcher.Single.class, single.potion());

    MatchCriteria.ItemBrewed listOf = (MatchCriteria.ItemBrewed) job.rewards().get(2).criteria();
    assertInstanceOf(KeyMatcher.ListOf.class, listOf.potion());
  }

  @Test
  void rejectsTagMatcherOnBrewedPotion(@TempDir Path dir) throws IOException {
    Path yml = dir.resolve("brew_tag.yml");
    Files.writeString(yml, """
        id: brew_tag
        display_name: "BrewTag"
        icon: minecraft:brewing_stand

        rewards:
          - on: item_brewed
            item: minecraft:potion
            potion: "#minecraft:whatever"
            reward: 8
        """, StandardCharsets.UTF_8);

    JobYamlLoader.LoadResult result = newLoader().loadDirectory(dir.toFile());
    assertTrue(result.errors().hasErrors());
    // rewards entry がスキップされ、job だけ残る (rewards が空)
    assertEquals(1, result.jobs().size());
    assertTrue(result.jobs().get(0).rewards().isEmpty());
  }

  @Test
  void parsesEntityKilledWithOptionalDimension(@TempDir Path dir) throws IOException {
    Path yml = dir.resolve("combat_dim.yml");
    Files.writeString(yml, """
        id: combat_dim
        display_name: "Combat"
        icon: minecraft:iron_sword

        rewards:
          - on: entity_killed
            entity: minecraft:zombie
            reward: 5
          - on: entity_killed
            entity: minecraft:blaze
            dimension: nether
            reward: 8
          - on: entity_killed
            entity: minecraft:enderman
            dimension: [nether, end]
            reward: 12
        """, StandardCharsets.UTF_8);

    JobYamlLoader.LoadResult result = newLoader().loadDirectory(dir.toFile());
    assertFalse(result.errors().hasErrors(), () -> "errors: " + result.errors().entries());
    JobDefinition job = result.jobs().get(0);
    assertEquals(3, job.rewards().size());

    var none = (MatchCriteria.EntityKilled) job.rewards().get(0).criteria();
    assertTrue(none.dimensions().isEmpty());

    var single = (MatchCriteria.EntityKilled) job.rewards().get(1).criteria();
    assertEquals(java.util.Set.of(Dimension.NETHER), single.dimensions());
    // 単調性ペナルティの派生キーは entity のみで決まり、dimension を含まない。
    assertEquals("kill:minecraft:blaze", job.rewards().get(1).derivedKey().value());

    var list = (MatchCriteria.EntityKilled) job.rewards().get(2).criteria();
    assertEquals(java.util.Set.of(Dimension.NETHER, Dimension.END), list.dimensions());
  }

  @Test
  void rejectsUnknownDimensionValue(@TempDir Path dir) throws IOException {
    Path yml = dir.resolve("bad_dim.yml");
    Files.writeString(yml, """
        id: bad_dim
        display_name: "BadDim"
        icon: minecraft:iron_sword

        rewards:
          - on: entity_killed
            entity: minecraft:zombie
            dimension: sky
            reward: 5
        """, StandardCharsets.UTF_8);
    JobYamlLoader.LoadResult result = newLoader().loadDirectory(dir.toFile());
    assertTrue(result.errors().hasErrors());
    assertEquals(1, result.jobs().size());
    assertTrue(result.jobs().get(0).rewards().isEmpty());
  }

  @Test
  void skipsInvalidRewardEntryButKeepsJob(@TempDir Path dir) throws IOException {
    Path yml = dir.resolve("mining.yml");
    Files.writeString(yml, """
        id: mining
        display_name: "Mining"
        icon: minecraft:iron_pickaxe

        rewards:
          - on: block_broken
            reward: 5
          - on: block_broken
            block: minecraft:stone
            reward: 2
        """, StandardCharsets.UTF_8);
    JobYamlLoader.LoadResult result = newLoader().loadDirectory(dir.toFile());
    assertTrue(result.errors().hasErrors());
    assertEquals(1, result.jobs().size());
    // valid entry のみ残る
    assertEquals(1, result.jobs().get(0).rewards().size());
  }
}
