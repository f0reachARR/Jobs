package me.f0reach.jobs.ui;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.ConsumeCategory;
import me.f0reach.jobs.domain.job.Dimension;
import me.f0reach.jobs.domain.job.JobDefinition;
import me.f0reach.jobs.domain.job.MatchCriteria;
import me.f0reach.jobs.domain.job.RepairSource;
import me.f0reach.jobs.domain.job.RewardAmount;
import me.f0reach.jobs.domain.job.RewardEntry;
import me.f0reach.jobs.domain.job.VarietyPenaltyConfig;
import me.f0reach.jobs.domain.matcher.KeyMatcher;
import me.f0reach.jobs.economy.AmountFormatter;
import me.f0reach.jobs.i18n.I18n;
import me.f0reach.jobs.i18n.LocaleRegistry;
import me.f0reach.jobs.registry.TagResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@link JobDefinition} を {@code JobConditionsDialog} の body として整形する。
 *
 * spec/07-ui.md 「職業条件の開示ダイアログ」を参照。
 * {@code discloseRewardAmount} が false のときは対象のみを列挙し、額は伏せる。
 * rare 報酬の存在は文言でのみ示唆し、chance / reward の数値は常に伏せる。
 */
public final class JobConditionsFormatter {

    /** body 1 職業あたりに載せる報酬エントリの上限。Bedrock 側の描画長を意識した保守値。 */
    static final int MAX_REWARD_LINES = 30;

    /** Tag を代表アイテムに展開する際に列挙する最大件数。超えた分は「〜など」で丸める。 */
    static final int MAX_TAG_REPRESENTATIVES = 3;

    private final I18n i18n;
    private final AmountFormatter amountFormatter;
    private final TagResolver tagResolver;

    public JobConditionsFormatter(I18n i18n, AmountFormatter amountFormatter, TagResolver tagResolver) {
        this.i18n = i18n;
        this.amountFormatter = amountFormatter;
        this.tagResolver = tagResolver;
    }

    public Component build(Player viewer, JobDefinition job, boolean discloseRewardAmount) {
        String locale = viewer == null ? LocaleRegistry.DEFAULT_LOCALE : viewer.locale().toString();
        return build(locale, job, discloseRewardAmount);
    }

    public Component build(String locale, JobDefinition job, boolean discloseRewardAmount) {
        List<Component> lines = new ArrayList<>();

        if (job.description() != null && !job.description().isBlank()) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_DESCRIPTION,
                    Placeholder.parsed("description", job.description())));
            lines.add(Component.empty());
        }

        lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_HEADER_REWARDS));
        List<RewardEntry> rewards = job.rewards();
        int shown = Math.min(rewards.size(), MAX_REWARD_LINES);
        for (int i = 0; i < shown; i++) {
            lines.add(formatRewardLine(locale, rewards.get(i), discloseRewardAmount));
        }
        if (rewards.size() > shown) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_REWARD_TRUNCATED,
                    Placeholder.parsed("count", Integer.toString(rewards.size() - shown))));
        }

        lines.add(Component.empty());
        lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_HEADER_VARIETY));
        lines.add(formatVariety(locale, job.varietyPenalty()));

        lines.add(Component.empty());
        lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_HEADER_ANTI_AUTOMATION));
        List<Component> antiLines = formatAntiAutomation(locale, job.antiAutomation());
        if (antiLines.isEmpty()) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_ANTI_AUTOMATION_NONE));
        } else {
            lines.addAll(antiLines);
        }

        return join(lines);
    }

    private Component formatRewardLine(String locale, RewardEntry entry, boolean discloseAmount) {
        Component label = i18n.format(locale,
                DialogTexts.DIALOG_INFO_REWARD_LABEL_PREFIX + entry.actionType().name().toLowerCase(Locale.ROOT));
        Component target = formatCriteria(locale, entry.criteria());
        Component amount = discloseAmount ? formatAmount(locale, entry.rewardAmount()) : Component.empty();
        Component rare = entry.rareBonus() != null
                ? i18n.format(locale, DialogTexts.DIALOG_INFO_REWARD_RARE_HINT)
                : Component.empty();
        return i18n.format(locale, DialogTexts.DIALOG_INFO_REWARD_TEMPLATE,
                Placeholder.component("label", label),
                Placeholder.component("target", target),
                Placeholder.component("amount", amount),
                Placeholder.component("rare", rare));
    }

    private Component formatAmount(String locale, RewardAmount amount) {
        return switch (amount) {
            case RewardAmount.Fixed f -> i18n.format(locale, DialogTexts.DIALOG_INFO_REWARD_AMOUNT_FIXED,
                    Placeholder.parsed("value", amountFormatter.format(f.value())));
            case RewardAmount.Range r -> i18n.format(locale, DialogTexts.DIALOG_INFO_REWARD_AMOUNT_RANGE,
                    Placeholder.parsed("min", amountFormatter.format(r.min())),
                    Placeholder.parsed("max", amountFormatter.format(r.max())));
        };
    }

    private Component formatCriteria(String locale, MatchCriteria criteria) {
        return switch (criteria) {
            case MatchCriteria.EntityKilled c -> concat(
                    formatKeyMatcher(locale, c.entity(), TagResolver.Kind.ENTITY_TYPES),
                    formatDimensions(locale, c.dimensions())
            );
            case MatchCriteria.BlockBroken c -> concat(
                    formatKeyMatcher(locale, c.block(), TagResolver.Kind.BLOCKS),
                    c.cropMature() ? i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_CROP_MATURE) : null,
                    c.viaTnt() ? i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_VIA_TNT) : null
            );
            case MatchCriteria.BlockPlaced c -> formatKeyMatcher(locale, c.block(), TagResolver.Kind.BLOCKS);
            case MatchCriteria.ItemFished c -> {
                if (c.item() == null) {
                    // treasure だけを指定した宝箱ループマッチ。suffix ではなく単独で示す。
                    yield i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_FISH_TREASURE_ONLY);
                }
                Component base = formatKeyMatcher(locale, c.item(), TagResolver.Kind.ITEMS);
                yield c.treasure()
                        ? concat(base, i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_FISH_TREASURE))
                        : base;
            }
            case MatchCriteria.ItemSmelted c -> formatKeyMatcher(locale, c.item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemCrafted c -> formatKeyMatcher(locale, c.item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemEnchanted c -> concat(
                    formatKeyMatcher(locale, c.item(), TagResolver.Kind.ITEMS),
                    formatEnchantment(locale, c.enchantment(), c.levelMin())
            );
            case MatchCriteria.ItemRepaired c -> concat(
                    formatKeyMatcher(locale, c.item(), TagResolver.Kind.ITEMS),
                    formatRepairSource(locale, c.source())
            );
            case MatchCriteria.EntityBred c -> formatKeyMatcher(locale, c.entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.EntityTamed c -> formatKeyMatcher(locale, c.entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.EntitySheared c -> formatKeyMatcher(locale, c.entity(), TagResolver.Kind.ENTITY_TYPES);
            case MatchCriteria.ItemConsumed c -> concat(
                    formatKeyMatcher(locale, c.item(), TagResolver.Kind.ITEMS),
                    formatConsumeCategory(locale, c.category())
            );
            case MatchCriteria.VillagerTraded c -> formatKeyMatcher(locale, c.item(), TagResolver.Kind.ITEMS);
            case MatchCriteria.ItemBrewed c -> concat(
                    formatKeyMatcher(locale, c.item(), TagResolver.Kind.ITEMS),
                    formatPotion(locale, c.potion())
            );
            case MatchCriteria.Advancement c -> Component.text(c.advancement().toString());
        };
    }

    private Component formatKeyMatcher(String locale, KeyMatcher matcher, TagResolver.Kind kind) {
        return switch (matcher) {
            case KeyMatcher.Single s -> translatableFor(s.key(), kind);
            case KeyMatcher.ListOf l -> {
                // ID 文字列順で並べたうえで各要素を translatable にする。
                // 表示は各クライアント言語に依存するため厳密な五十音順は取れないが、
                // サーバ側は決定的な順序を保つ。
                TreeSet<NamespacedKey> sorted = new TreeSet<>(NamespacedKey::compareTo);
                sorted.addAll(l.keys());
                String sep = i18n.registry().get(locale, DialogTexts.DIALOG_INFO_TARGET_LIST_SEPARATOR);
                Component joined = Component.empty();
                boolean first = true;
                for (NamespacedKey k : sorted) {
                    if (!first) joined = joined.append(Component.text(sep));
                    joined = joined.append(translatableFor(k, kind));
                    first = false;
                }
                yield joined;
            }
            case KeyMatcher.Tag t -> formatTag(locale, t.tag(), kind);
        };
    }

    /**
     * Tag を「代表 N 件 + など」で表示する。resolve に失敗した場合 (データパック未反映など) は
     * 従来通り `#tag (タグ)` にフォールバックし、クライアントに解釈可能な形で漏らす。
     */
    private Component formatTag(String locale, NamespacedKey tagKey, TagResolver.Kind kind) {
        Set<NamespacedKey> members = tagResolver.resolve(kind, tagKey);
        if (members.isEmpty()) {
            return concat(
                    Component.text("#" + tagKey),
                    i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_TAG_SUFFIX)
            );
        }
        TreeSet<NamespacedKey> sorted = new TreeSet<>(NamespacedKey::compareTo);
        sorted.addAll(members);
        String sep = i18n.registry().get(locale, DialogTexts.DIALOG_INFO_TARGET_LIST_SEPARATOR);
        Component items = Component.empty();
        int count = 0;
        boolean first = true;
        for (NamespacedKey k : sorted) {
            if (count >= MAX_TAG_REPRESENTATIVES) break;
            if (!first) items = items.append(Component.text(sep));
            items = items.append(translatableFor(k, kind));
            first = false;
            count++;
        }
        return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_TAG_REPRESENTATIVE,
                Placeholder.component("items", items));
    }

    /**
     * NamespacedKey を Component.translatable に変換する。翻訳はクライアント側で行うため
     * サーバ側 locale では引かない (フォールバックとして key.toString() を渡し、翻訳キー未登録の
     * クライアントや PlainText 化された場合の欠落を防ぐ)。
     */
    private static Component translatableFor(NamespacedKey key, TagResolver.Kind kind) {
        String translationKey = resolveTranslationKey(key, kind);
        String fallback = key.toString();
        if (translationKey == null) return Component.text(fallback);
        return Component.translatable(translationKey, fallback);
    }

    private static String resolveTranslationKey(NamespacedKey key, TagResolver.Kind kind) {
        return switch (kind) {
            case BLOCKS, ITEMS -> {
                Material m = Registry.MATERIAL.get(key);
                yield m == null ? null : m.translationKey();
            }
            case ENTITY_TYPES -> {
                EntityType e = Registry.ENTITY_TYPE.get(key);
                yield e == null ? null : e.translationKey();
            }
        };
    }

    private Component formatEnchantment(String locale, NamespacedKey enchantment, int levelMin) {
        if (enchantment == null && levelMin <= 0) return null;
        if (enchantment != null && levelMin > 0) {
            return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_ENCHANT_WITH_LEVEL,
                    Placeholder.component("enchant", enchantmentComponent(enchantment)),
                    Placeholder.parsed("level_min", Integer.toString(levelMin)));
        }
        if (enchantment != null) {
            return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_ENCHANT_ONLY,
                    Placeholder.component("enchant", enchantmentComponent(enchantment)));
        }
        return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_LEVEL_ONLY,
                Placeholder.parsed("level_min", Integer.toString(levelMin)));
    }

    private static Component enchantmentComponent(NamespacedKey key) {
        Enchantment ench = Registry.ENCHANTMENT.get(key);
        String fallback = key.toString();
        if (ench == null) return Component.text(fallback);
        // Bukkit Translatable の translationKey() は @Deprecated だが Adventure 側の
        // net.kyori.adventure.translation.Translatable#translationKey() は使える。
        String translationKey = ((net.kyori.adventure.translation.Translatable) ench).translationKey();
        return Component.translatable(translationKey, fallback);
    }

    private Component formatRepairSource(String locale, RepairSource source) {
        if (source == null) return null;
        return switch (source) {
            case ANVIL -> i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_REPAIR_ANVIL);
            case MENDING -> i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_REPAIR_MENDING);
        };
    }

    private Component formatDimensions(String locale, java.util.Set<Dimension> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) return null;
        // enum の宣言順 (OVERWORLD → NETHER → END) で安定した表示にする。
        List<Component> parts = new ArrayList<>();
        for (Dimension d : Dimension.values()) {
            if (dimensions.contains(d)) parts.add(i18n.format(locale, dimensionKey(d)));
        }
        String sep = i18n.registry().get(locale, DialogTexts.DIALOG_INFO_TARGET_LIST_SEPARATOR);
        Component joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) joined = joined.append(Component.text(sep));
            joined = joined.append(parts.get(i));
        }
        return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_DIMENSION,
                Placeholder.component("dims", joined));
    }

    private static String dimensionKey(Dimension d) {
        return switch (d) {
            case OVERWORLD -> DialogTexts.DIALOG_INFO_TARGET_DIMENSION_OVERWORLD;
            case NETHER -> DialogTexts.DIALOG_INFO_TARGET_DIMENSION_NETHER;
            case END -> DialogTexts.DIALOG_INFO_TARGET_DIMENSION_END;
        };
    }

    private Component formatPotion(String locale, KeyMatcher potion) {
        if (potion == null) return null;
        return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_POTION,
                Placeholder.component("potion", formatPotionMatcher(locale, potion)));
    }

    /**
     * Potion 用の matcher フォーマット。PotionType は TagResolver.Kind を持たないため
     * KeyMatcher.Tag は parser で禁止されており、Single / ListOf のみを想定する。
     * KeyMatcher.Tag が万一渡された場合は key 文字列にフォールバックする。
     */
    private Component formatPotionMatcher(String locale, KeyMatcher matcher) {
        return switch (matcher) {
            case KeyMatcher.Single s -> potionComponent(s.key());
            case KeyMatcher.ListOf l -> {
                TreeSet<NamespacedKey> sorted = new TreeSet<>(NamespacedKey::compareTo);
                sorted.addAll(l.keys());
                String sep = i18n.registry().get(locale, DialogTexts.DIALOG_INFO_TARGET_LIST_SEPARATOR);
                Component joined = Component.empty();
                boolean first = true;
                for (NamespacedKey k : sorted) {
                    if (!first) joined = joined.append(Component.text(sep));
                    joined = joined.append(potionComponent(k));
                    first = false;
                }
                yield joined;
            }
            case KeyMatcher.Tag t -> Component.text("#" + t.tag());
        };
    }

    private static Component potionComponent(NamespacedKey key) {
        PotionType type = Registry.POTION.get(key);
        String fallback = key.toString();
        if (type == null) return Component.text(fallback);
        // PotionType 自体は translationKey を持たないため、主効果 (PotionEffectType) の
        // translationKey にフォールバックする。AWKWARD/MUNDANE/WATER 等の効果無し potion は
        // getEffectType() が null を返すため、元のキー文字列で表示する。
        var effect = type.getEffectType();
        if (effect == null) return Component.text(fallback);
        String translationKey = ((net.kyori.adventure.translation.Translatable) effect).translationKey();
        return Component.translatable(translationKey, fallback);
    }

    private Component formatConsumeCategory(String locale, ConsumeCategory category) {
        if (category == null) return null;
        return switch (category) {
            case FOOD -> i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_CONSUME_FOOD);
            case DRINK -> i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_CONSUME_DRINK);
        };
    }

    private Component formatVariety(String locale, VarietyPenaltyConfig config) {
        if (config == null || !config.enabled()) {
            return i18n.format(locale, DialogTexts.DIALOG_INFO_VARIETY_NONE);
        }
        String disclosed = config.disclosedMessage();
        if (disclosed != null && !disclosed.isBlank()) {
            return i18n.format(locale, DialogTexts.DIALOG_INFO_VARIETY_DISCLOSED,
                    Placeholder.parsed("disclosed", disclosed));
        }
        return i18n.format(locale, DialogTexts.DIALOG_INFO_VARIETY_ACTIVE_NO_MESSAGE);
    }

    private List<Component> formatAntiAutomation(String locale, AntiAutomationConfig config) {
        List<Component> lines = new ArrayList<>();
        if (config == null) return lines;
        if (config.spawnerOriginKills() == AntiAutomationConfig.SpawnerOriginKills.ZERO) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_ANTI_AUTOMATION_SPAWNER_ORIGIN_KILL));
        }
        if (config.unplantedCropHarvest() == AntiAutomationConfig.UnplantedCropHarvest.ZERO) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_ANTI_AUTOMATION_UNPLANTED_CROP_HARVEST));
        }
        var rp = config.recentlyPlacedBreak();
        if (rp != null && rp.enabled()) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_ANTI_AUTOMATION_RECENTLY_PLACED_BREAK,
                    Placeholder.parsed("window_sec", Integer.toString(rp.windowSec()))));
        }
        var af = config.autoFedProcessing();
        if (af != null && af.enabled()) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_ANTI_AUTOMATION_AUTO_FED_PROCESSING));
        }
        var vt = config.villagerRepeatTrade();
        if (vt != null && vt.enabled()) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_ANTI_AUTOMATION_VILLAGER_REPEAT_TRADE,
                    Placeholder.parsed("cooldown_sec", Integer.toString(vt.cooldownSec()))));
        }
        if (config.breedNonPlayerBreeder() == AntiAutomationConfig.BreedNonPlayerBreeder.ZERO) {
            lines.add(i18n.format(locale, DialogTexts.DIALOG_INFO_ANTI_AUTOMATION_BREED_NON_PLAYER_BREEDER));
        }
        return lines;
    }

    private static Component concat(Component... parts) {
        Component result = Component.empty();
        for (Component part : parts) {
            if (part != null) result = result.append(part);
        }
        return result;
    }

    private static Component join(List<Component> lines) {
        Component result = Component.empty();
        boolean first = true;
        for (Component line : lines) {
            if (!first) result = result.append(Component.newline());
            result = result.append(line);
            first = false;
        }
        return result;
    }
}
