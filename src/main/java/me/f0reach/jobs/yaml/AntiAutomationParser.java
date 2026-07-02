package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import org.bukkit.configuration.ConfigurationSection;

/**
 * anti_automation セクションのパーサ。
 * config.yml のグローバルデフォルトと per-job YAML の両方で使う。
 *
 * <p>各キーは 3 値をとる。
 * <ul>
 *   <li>キー未指定: field が null（呼び出し側で default とマージされる）</li>
 *   <li>スカラー {@code "zero"} または section: 明示的に有効化</li>
 *   <li>スカラー {@code "off"} または {@code enabled: false} を持つ section: 明示的に無効化</li>
 * </ul>
 */
public final class AntiAutomationParser {

    private static final int DEFAULT_RECENTLY_PLACED_WINDOW_SEC = 3600;
    private static final int DEFAULT_OPERATOR_TTL_SEC = 60;
    private static final int DEFAULT_TRADE_COOLDOWN_SEC = 60;

    public AntiAutomationConfig parse(ConfigurationSection section, String path) {
        if (section == null) {
            return AntiAutomationConfig.empty();
        }

        AntiAutomationConfig.SpawnerOriginKills spawner = parseEnumFlag(
                section, "spawner_origin_kills", path,
                AntiAutomationConfig.SpawnerOriginKills.ZERO,
                AntiAutomationConfig.SpawnerOriginKills.DISABLED
        );

        AntiAutomationConfig.UnplantedCropHarvest unplanted = parseEnumFlag(
                section, "unplanted_crop_harvest", path,
                AntiAutomationConfig.UnplantedCropHarvest.ZERO,
                AntiAutomationConfig.UnplantedCropHarvest.DISABLED
        );

        AntiAutomationConfig.RecentlyPlacedBreak recentlyPlaced = parseRecentlyPlacedBreak(
                section, path + ".recently_placed_break");

        AntiAutomationConfig.AutoFedProcessing autoFed = parseAutoFedProcessing(
                section, path + ".auto_fed_processing");

        AntiAutomationConfig.VillagerRepeatTrade repeat = parseVillagerRepeatTrade(
                section, path + ".villager_repeat_trade");

        AntiAutomationConfig.BreedNonPlayerBreeder breeder = parseEnumFlag(
                section, "breed_non_player_breeder", path,
                AntiAutomationConfig.BreedNonPlayerBreeder.ZERO,
                AntiAutomationConfig.BreedNonPlayerBreeder.DISABLED
        );

        return new AntiAutomationConfig(spawner, unplanted, recentlyPlaced, autoFed, repeat, breeder);
    }

    private AntiAutomationConfig.RecentlyPlacedBreak parseRecentlyPlacedBreak(
            ConfigurationSection parent, String path) {
        if (!parent.isSet("recently_placed_break")) return null;
        if (isOffScalar(parent, "recently_placed_break")) {
            return AntiAutomationConfig.RecentlyPlacedBreak.disabled();
        }
        ConfigurationSection sec = parent.getConfigurationSection("recently_placed_break");
        if (sec == null) {
            throw new YamlParseException(path + ": expected 'off' or a map");
        }
        if (!sec.getBoolean("enabled", true)) {
            return AntiAutomationConfig.RecentlyPlacedBreak.disabled();
        }
        int windowSec = sec.getInt("window_sec", DEFAULT_RECENTLY_PLACED_WINDOW_SEC);
        return new AntiAutomationConfig.RecentlyPlacedBreak(windowSec, true);
    }

    private AntiAutomationConfig.AutoFedProcessing parseAutoFedProcessing(
            ConfigurationSection parent, String path) {
        if (!parent.isSet("auto_fed_processing")) return null;
        if (isOffScalar(parent, "auto_fed_processing")) {
            return AntiAutomationConfig.AutoFedProcessing.disabled();
        }
        ConfigurationSection sec = parent.getConfigurationSection("auto_fed_processing");
        if (sec == null) {
            throw new YamlParseException(path + ": expected 'off' or a map");
        }
        if (!sec.getBoolean("enabled", true)) {
            return AntiAutomationConfig.AutoFedProcessing.disabled();
        }
        int ttl = sec.getInt("operator_ttl_sec", DEFAULT_OPERATOR_TTL_SEC);
        return new AntiAutomationConfig.AutoFedProcessing(ttl, true);
    }

    private AntiAutomationConfig.VillagerRepeatTrade parseVillagerRepeatTrade(
            ConfigurationSection parent, String path) {
        if (!parent.isSet("villager_repeat_trade")) return null;
        if (isOffScalar(parent, "villager_repeat_trade")) {
            return AntiAutomationConfig.VillagerRepeatTrade.disabled();
        }
        ConfigurationSection sec = parent.getConfigurationSection("villager_repeat_trade");
        if (sec == null) {
            throw new YamlParseException(path + ": expected 'off' or a map");
        }
        if (!sec.getBoolean("enabled", true)) {
            return AntiAutomationConfig.VillagerRepeatTrade.disabled();
        }
        int cooldown = sec.getInt("cooldown_sec", DEFAULT_TRADE_COOLDOWN_SEC);
        String scope = sec.getString("scope", "recipe");
        if (!"recipe".equalsIgnoreCase(scope)) {
            throw new YamlParseException(path + ".scope: only 'recipe' is supported");
        }
        return new AntiAutomationConfig.VillagerRepeatTrade(cooldown, true);
    }

    /** section 直下のキーがスカラーで {@code "off"} かどうかを見る。 */
    private boolean isOffScalar(ConfigurationSection parent, String key) {
        Object raw = parent.get(key);
        if (raw instanceof String s) {
            return "off".equalsIgnoreCase(s);
        }
        // SnakeYAML が YAML 1.1 で "off" を Boolean.FALSE に解釈するケース
        if (raw instanceof Boolean b) {
            return !b;
        }
        return false;
    }

    private <T extends Enum<T>> T parseEnumFlag(
            ConfigurationSection section,
            String key,
            String parentPath,
            T onValue,
            T offValue
    ) {
        if (!section.isSet(key)) return null;
        Object raw = section.get(key);
        String value;
        if (raw instanceof Boolean b) {
            value = b ? "on" : "off";
        } else if (raw instanceof String s) {
            value = s;
        } else {
            throw new YamlParseException(parentPath + "." + key + ": expected 'zero' or 'off'");
        }
        if ("zero".equalsIgnoreCase(value)) return onValue;
        if ("off".equalsIgnoreCase(value)) return offValue;
        throw new YamlParseException(parentPath + "." + key + ": only 'zero' or 'off' are supported");
    }
}
