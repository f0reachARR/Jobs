package me.f0reach.jobs.ui;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.ConsumeCategory;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.JobId;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RareBonus;
import me.f0reach.jobs.domain.job.RepairSource;
import me.f0reach.jobs.domain.job.RewardAmount;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import me.f0reach.jobs.economy.AmountFormatter;
import me.f0reach.jobs.i18n.I18n;
import me.f0reach.jobs.i18n.LocaleRegistry;
import me.f0reach.jobs.registry.ActionKeyDeriver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobConditionsFormatterTest {

    private static final String LOCALE = "ja_jp";

    @BeforeAll
    static void setUp() { MockBukkit.mock(); }

    @AfterAll
    static void tearDown() { MockBukkit.unmock(); }

    private JobConditionsFormatter formatter() {
        Map<String, String> keys = new LinkedHashMap<>();
        // headers
        keys.put("dialog.info.description", "説明: <description>");
        keys.put("dialog.info.header.rewards", "== 報酬 ==");
        keys.put("dialog.info.header.variety", "== 単調性 ==");
        keys.put("dialog.info.header.anti_automation", "== 自動化対策 ==");
        // reward template
        keys.put("dialog.info.reward.template", "<label>: <target><amount><rare>");
        keys.put("dialog.info.reward.label.entity_killed", "討伐");
        keys.put("dialog.info.reward.label.block_broken", "採掘");
        keys.put("dialog.info.reward.label.item_repaired", "修理");
        keys.put("dialog.info.reward.label.item_enchanted", "付呪");
        keys.put("dialog.info.reward.label.item_fished", "釣り");
        keys.put("dialog.info.reward.label.item_consumed", "消費");
        keys.put("dialog.info.reward.amount.fixed", " → <value>");
        keys.put("dialog.info.reward.amount.range", " → <min>〜<max>");
        keys.put("dialog.info.reward.rare_hint", " (レア報酬あり)");
        keys.put("dialog.info.reward.truncated", "…ほか <count> 件");
        // target extras
        keys.put("dialog.info.target.list_separator", " / ");
        keys.put("dialog.info.target.tag_suffix", " (タグ)");
        keys.put("dialog.info.target.crop_mature", " (完熟)");
        keys.put("dialog.info.target.via_tnt", " (TNT)");
        keys.put("dialog.info.target.fish_treasure", " (宝箱)");
        keys.put("dialog.info.target.fish_treasure_only", "宝箱");
        keys.put("dialog.info.target.enchant_with_level", " (<enchant> Lv<level_min>+)");
        keys.put("dialog.info.target.enchant_only", " (<enchant>)");
        keys.put("dialog.info.target.level_only", " (Lv<level_min>+)");
        keys.put("dialog.info.target.repair_anvil", " (金床)");
        keys.put("dialog.info.target.repair_mending", " (mending)");
        keys.put("dialog.info.target.consume_food", " (food)");
        keys.put("dialog.info.target.consume_drink", " (drink)");
        keys.put("dialog.info.target.potion", " (ポーション: <potion>)");
        keys.put("dialog.info.reward.label.item_brewed", "醸造");
        // variety
        keys.put("dialog.info.variety.none", "なし");
        keys.put("dialog.info.variety.disclosed", "<disclosed>");
        keys.put("dialog.info.variety.active_no_message", "有効");
        // anti-automation
        keys.put("dialog.info.anti_automation.none", "なし");
        keys.put("dialog.info.anti_automation.spawner_origin_kill", "スポナー討伐 0");
        keys.put("dialog.info.anti_automation.unplanted_crop_harvest", "未植 0");
        keys.put("dialog.info.anti_automation.recently_placed_break", "直近 0 (<window_sec>s)");
        keys.put("dialog.info.anti_automation.auto_fed_processing", "自動投入 0");
        keys.put("dialog.info.anti_automation.villager_repeat_trade", "取引 CD (<cooldown_sec>s)");
        keys.put("dialog.info.anti_automation.breed_non_player_breeder", "非 Player 交配 0");

        LocaleRegistry reg = LocaleRegistry.forTesting(Map.of(LOCALE, keys));
        return new JobConditionsFormatter(new I18n(reg), fakeAmountFormatter());
    }

    /** テストでは Vault provider を持たないため、整数は "N coins"、小数は "N.NN coins"。 */
    private AmountFormatter fakeAmountFormatter() {
        return amount -> {
            if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
                return ((long) amount) + " coins";
            }
            return String.format(java.util.Locale.ROOT, "%.2f coins", amount);
        };
    }

    private String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private RewardEntry entry(MatchCriteria criteria, RewardAmount amount, RareBonus rare) {
        return new RewardEntry(criteria.actionType(), criteria, amount, rare,
                new ActionKeyDeriver().derive(criteria));
    }

    @Test
    void includesDescriptionRewardsVarietyAntiAutomation() {
        RewardEntry killZombie = entry(
                new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie"))),
                new RewardAmount.Fixed(5), null);
        RewardEntry mining = entry(
                new MatchCriteria.BlockBroken(new KeyMatcher.Single(NamespacedKey.minecraft("diamond_ore")), false, false),
                new RewardAmount.Range(10, 20), null);

        JobDefinition job = new JobDefinition(
                new JobId("combat"), "討伐職", "戦って稼ぐ",
                NamespacedKey.minecraft("iron_sword"),
                List.of(killZombie, mining),
                new VarietyPenaltyConfig(true, 30, List.of(new VarietyPenaltyConfig.CurvePoint(1.0, 0.5)), "飽きた", true),
                new AntiAutomationConfig(
                        AntiAutomationConfig.SpawnerOriginKills.ZERO,
                        null, null, null, null, null));

        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("戦って稼ぐ"));
        assertTrue(body.contains("討伐: minecraft:zombie → 5 coins"));
        assertTrue(body.contains("採掘: minecraft:diamond_ore → 10 coins〜20 coins"));
        assertTrue(body.contains("飽きた"));
        assertTrue(body.contains("スポナー討伐 0"));
    }

    @Test
    void hidesAmountWhenDiscloseFlagIsFalse() {
        RewardEntry killZombie = entry(
                new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("zombie"))),
                new RewardAmount.Fixed(5), null);
        JobDefinition job = new JobDefinition(
                new JobId("combat"), "討伐職", null, NamespacedKey.minecraft("iron_sword"),
                List.of(killZombie),
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());

        String body = plain(formatter().build(LOCALE, job, false));
        assertTrue(body.contains("討伐: minecraft:zombie"));
        assertFalse(body.contains("→"), () -> "should not contain amount arrow; got:\n" + body);
        assertFalse(body.contains("coins"), () -> "should not contain amount unit; got:\n" + body);
    }

    @Test
    void rareBonusShownAsExistenceOnly() {
        RewardEntry rareKill = entry(
                new MatchCriteria.EntityKilled(new KeyMatcher.Single(NamespacedKey.minecraft("skeleton"))),
                new RewardAmount.Fixed(5),
                new RareBonus(0.001, new RewardAmount.Fixed(100000), "<player> gold!"));
        JobDefinition job = new JobDefinition(
                new JobId("combat"), "討伐職", null, NamespacedKey.minecraft("iron_sword"),
                List.of(rareKill),
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());

        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("(レア報酬あり)"));
        // chance も rare の額も出さない
        assertFalse(body.contains("0.001"));
        assertFalse(body.contains("100000"));
    }

    @Test
    void listAndTagFormatting() {
        RewardEntry listKill = entry(
                new MatchCriteria.EntityKilled(new KeyMatcher.ListOf(List.of(
                        NamespacedKey.minecraft("zombie"), NamespacedKey.minecraft("husk")))),
                new RewardAmount.Fixed(3), null);
        RewardEntry tagKill = entry(
                new MatchCriteria.EntityKilled(new KeyMatcher.Tag(NamespacedKey.minecraft("undead"))),
                new RewardAmount.Fixed(2), null);
        JobDefinition job = new JobDefinition(
                new JobId("combat"), "討伐職", null, NamespacedKey.minecraft("iron_sword"),
                List.of(listKill, tagKill),
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());

        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("minecraft:husk / minecraft:zombie"));
        assertTrue(body.contains("#minecraft:undead (タグ)"));
    }

    @Test
    void cropMatureAndViaTntSuffixes() {
        RewardEntry mature = entry(
                new MatchCriteria.BlockBroken(new KeyMatcher.Single(NamespacedKey.minecraft("wheat")), true, false),
                new RewardAmount.Fixed(1), null);
        RewardEntry viaTnt = entry(
                new MatchCriteria.BlockBroken(new KeyMatcher.Single(NamespacedKey.minecraft("stone")), false, true),
                new RewardAmount.Fixed(1), null);
        JobDefinition job = new JobDefinition(
                new JobId("farming"), "農", null, NamespacedKey.minecraft("wheat"),
                List.of(mature, viaTnt),
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());

        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("(完熟)"), () -> body);
        assertTrue(body.contains("(TNT)"), () -> body);
    }

    @Test
    void enchantmentRepairConsumeExtras() {
        RewardEntry enchWithLevel = entry(
                new MatchCriteria.ItemEnchanted(new KeyMatcher.Single(NamespacedKey.minecraft("diamond_sword")),
                        NamespacedKey.minecraft("sharpness"), 3),
                new RewardAmount.Fixed(1), null);
        RewardEntry repairAnvil = entry(
                new MatchCriteria.ItemRepaired(new KeyMatcher.Single(NamespacedKey.minecraft("iron_pickaxe")),
                        RepairSource.ANVIL),
                new RewardAmount.Fixed(1), null);
        RewardEntry consumeFood = entry(
                new MatchCriteria.ItemConsumed(new KeyMatcher.Single(NamespacedKey.minecraft("bread")),
                        ConsumeCategory.FOOD),
                new RewardAmount.Fixed(1), null);
        JobDefinition job = new JobDefinition(
                new JobId("crafter"), "生産", null, NamespacedKey.minecraft("crafting_table"),
                List.of(enchWithLevel, repairAnvil, consumeFood),
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());

        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("(minecraft:sharpness Lv3+)"));
        assertTrue(body.contains("(金床)"));
        assertTrue(body.contains("(food)"));
    }

    @Test
    void brewedWithPotionAppendsPotionSuffix() {
        RewardEntry brewedNoPotion = entry(
                new MatchCriteria.ItemBrewed(
                        new KeyMatcher.Single(NamespacedKey.minecraft("potion")), null),
                new RewardAmount.Fixed(8), null);
        RewardEntry brewedWithPotion = entry(
                new MatchCriteria.ItemBrewed(
                        new KeyMatcher.Single(NamespacedKey.minecraft("splash_potion")),
                        new KeyMatcher.Single(NamespacedKey.minecraft("strong_healing"))),
                new RewardAmount.Fixed(20), null);
        JobDefinition job = new JobDefinition(
                new JobId("brewer"), "醸造", null, NamespacedKey.minecraft("brewing_stand"),
                List.of(brewedNoPotion, brewedWithPotion),
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());

        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("醸造: minecraft:potion"), () -> body);
        assertFalse(body.contains("minecraft:potion (ポーション:"), () -> body);
        assertTrue(body.contains("醸造: minecraft:splash_potion (ポーション: minecraft:strong_healing)"),
                () -> body);
    }

    @Test
    void treasureFishingWithoutItem() {
        RewardEntry treasure = entry(
                new MatchCriteria.ItemFished(null, true),
                new RewardAmount.Fixed(50), null);
        JobDefinition job = new JobDefinition(
                new JobId("explorer"), "探検", null, NamespacedKey.minecraft("fishing_rod"),
                List.of(treasure),
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());
        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("釣り: 宝箱"), () -> body);
    }

    @Test
    void varietyDisabledFallsBackToNone() {
        JobDefinition job = new JobDefinition(
                new JobId("x"), "X", null, NamespacedKey.minecraft("stone"),
                List.of(),
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());
        String body = plain(formatter().build(LOCALE, job, true));
        int varietyStart = body.indexOf("== 単調性 ==");
        assertTrue(varietyStart >= 0);
        assertTrue(body.substring(varietyStart).contains("なし"));
    }

    @Test
    void allAntiAutomationSectionsRendered() {
        AntiAutomationConfig full = new AntiAutomationConfig(
                AntiAutomationConfig.SpawnerOriginKills.ZERO,
                AntiAutomationConfig.UnplantedCropHarvest.ZERO,
                new AntiAutomationConfig.RecentlyPlacedBreak(3600, true),
                new AntiAutomationConfig.AutoFedProcessing(60, true),
                new AntiAutomationConfig.VillagerRepeatTrade(60, true),
                AntiAutomationConfig.BreedNonPlayerBreeder.ZERO);
        JobDefinition job = new JobDefinition(
                new JobId("x"), "X", null, NamespacedKey.minecraft("stone"),
                List.of(),
                VarietyPenaltyConfig.disabled(), full);
        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("スポナー討伐 0"));
        assertTrue(body.contains("未植 0"));
        assertTrue(body.contains("直近 0 (3600s)"));
        assertTrue(body.contains("自動投入 0"));
        assertTrue(body.contains("取引 CD (60s)"));
        assertTrue(body.contains("非 Player 交配 0"));
    }

    @Test
    void rewardEntriesTruncatedWhenExceedingLimit() {
        List<RewardEntry> many = new java.util.ArrayList<>();
        for (int i = 0; i < JobConditionsFormatter.MAX_REWARD_LINES + 5; i++) {
            many.add(entry(
                    new MatchCriteria.EntityKilled(new KeyMatcher.Single(
                            NamespacedKey.minecraft("mob_" + i))),
                    new RewardAmount.Fixed(1), null));
        }
        JobDefinition job = new JobDefinition(
                new JobId("x"), "X", null, NamespacedKey.minecraft("stone"),
                many,
                VarietyPenaltyConfig.disabled(), AntiAutomationConfig.empty());
        String body = plain(formatter().build(LOCALE, job, true));
        assertTrue(body.contains("…ほか 5 件"));
    }
}
