package me.f0reach.jobs.ui;

import me.f0reach.jobs.domain.job.ActionType;
import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import me.f0reach.jobs.domain.job.ConsumeCategory;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private final I18n i18n;
    private final AmountFormatter amountFormatter;

    public JobConditionsFormatter(I18n i18n, AmountFormatter amountFormatter) {
        this.i18n = i18n;
        this.amountFormatter = amountFormatter;
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
            case MatchCriteria.EntityKilled c -> formatKeyMatcher(locale, c.entity());
            case MatchCriteria.BlockBroken c -> concat(
                    formatKeyMatcher(locale, c.block()),
                    c.cropMature() ? i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_CROP_MATURE) : null,
                    c.viaTnt() ? i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_VIA_TNT) : null
            );
            case MatchCriteria.BlockPlaced c -> formatKeyMatcher(locale, c.block());
            case MatchCriteria.ItemFished c -> {
                if (c.item() == null) {
                    // treasure だけを指定した宝箱ループマッチ。suffix ではなく単独で示す。
                    yield i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_FISH_TREASURE_ONLY);
                }
                Component base = formatKeyMatcher(locale, c.item());
                yield c.treasure()
                        ? concat(base, i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_FISH_TREASURE))
                        : base;
            }
            case MatchCriteria.ItemSmelted c -> formatKeyMatcher(locale, c.item());
            case MatchCriteria.ItemCrafted c -> formatKeyMatcher(locale, c.item());
            case MatchCriteria.ItemEnchanted c -> concat(
                    formatKeyMatcher(locale, c.item()),
                    formatEnchantment(locale, c.enchantment(), c.levelMin())
            );
            case MatchCriteria.ItemRepaired c -> concat(
                    formatKeyMatcher(locale, c.item()),
                    formatRepairSource(locale, c.source())
            );
            case MatchCriteria.EntityBred c -> formatKeyMatcher(locale, c.entity());
            case MatchCriteria.EntityTamed c -> formatKeyMatcher(locale, c.entity());
            case MatchCriteria.EntitySheared c -> formatKeyMatcher(locale, c.entity());
            case MatchCriteria.ItemConsumed c -> concat(
                    formatKeyMatcher(locale, c.item()),
                    formatConsumeCategory(locale, c.category())
            );
            case MatchCriteria.VillagerTraded c -> formatKeyMatcher(locale, c.item());
            case MatchCriteria.ItemBrewed c -> formatKeyMatcher(locale, c.item());
            case MatchCriteria.Advancement c -> Component.text(c.advancement().toString());
        };
    }

    private Component formatKeyMatcher(String locale, KeyMatcher matcher) {
        return switch (matcher) {
            case KeyMatcher.Single s -> Component.text(s.key().toString());
            case KeyMatcher.ListOf l -> {
                TreeSet<String> sorted = new TreeSet<>();
                for (NamespacedKey k : l.keys()) sorted.add(k.toString());
                String sep = i18n.registry().get(locale, DialogTexts.DIALOG_INFO_TARGET_LIST_SEPARATOR);
                yield Component.text(String.join(sep, sorted));
            }
            case KeyMatcher.Tag t -> concat(
                    Component.text("#" + t.tag()),
                    i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_TAG_SUFFIX)
            );
        };
    }

    private Component formatEnchantment(String locale, NamespacedKey enchantment, int levelMin) {
        if (enchantment == null && levelMin <= 0) return null;
        if (enchantment != null && levelMin > 0) {
            return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_ENCHANT_WITH_LEVEL,
                    Placeholder.parsed("enchant", enchantment.toString()),
                    Placeholder.parsed("level_min", Integer.toString(levelMin)));
        }
        if (enchantment != null) {
            return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_ENCHANT_ONLY,
                    Placeholder.parsed("enchant", enchantment.toString()));
        }
        return i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_LEVEL_ONLY,
                Placeholder.parsed("level_min", Integer.toString(levelMin)));
    }

    private Component formatRepairSource(String locale, RepairSource source) {
        if (source == null) return null;
        return switch (source) {
            case ANVIL -> i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_REPAIR_ANVIL);
            case MENDING -> i18n.format(locale, DialogTexts.DIALOG_INFO_TARGET_REPAIR_MENDING);
        };
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
