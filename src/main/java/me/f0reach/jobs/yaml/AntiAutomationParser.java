package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

public final class AntiAutomationParser {

    private static final int DEFAULT_RECENTLY_PLACED_WINDOW_SEC = 3600;
    private static final int DEFAULT_OPERATOR_TTL_SEC = 60;
    private static final int DEFAULT_TRADE_COOLDOWN_SEC = 60;

    public AntiAutomationConfig parse(ConfigurationSection section, String path) {
        if (section == null) {
            return AntiAutomationConfig.empty();
        }

        AntiAutomationConfig.SpawnerOriginKills spawner = parseZeroEnum(
                section.getString("spawner_origin_kills"),
                path + ".spawner_origin_kills",
                AntiAutomationConfig.SpawnerOriginKills.ZERO
        );

        AntiAutomationConfig.UnplantedCropHarvest unplanted = parseZeroEnum(
                section.getString("unplanted_crop_harvest"),
                path + ".unplanted_crop_harvest",
                AntiAutomationConfig.UnplantedCropHarvest.ZERO
        );

        AntiAutomationConfig.RecentlyPlacedBreak recentlyPlaced = null;
        ConfigurationSection recentlyPlacedSection = section.getConfigurationSection("recently_placed_break");
        if (recentlyPlacedSection != null) {
            int windowSec = recentlyPlacedSection.getInt("window_sec", DEFAULT_RECENTLY_PLACED_WINDOW_SEC);
            recentlyPlaced = new AntiAutomationConfig.RecentlyPlacedBreak(windowSec);
        }

        AntiAutomationConfig.AutoFedProcessing autoFed = null;
        ConfigurationSection autoFedSection = section.getConfigurationSection("auto_fed_processing");
        if (autoFedSection != null) {
            int ttl = autoFedSection.getInt("operator_ttl_sec", DEFAULT_OPERATOR_TTL_SEC);
            autoFed = new AntiAutomationConfig.AutoFedProcessing(ttl);
        }

        AntiAutomationConfig.VillagerRepeatTrade repeat = null;
        ConfigurationSection repeatSection = section.getConfigurationSection("villager_repeat_trade");
        if (repeatSection != null) {
            int cooldown = repeatSection.getInt("cooldown_sec", DEFAULT_TRADE_COOLDOWN_SEC);
            String scope = repeatSection.getString("scope", "recipe");
            if (!"recipe".equalsIgnoreCase(scope)) {
                throw new YamlParseException(path + ".villager_repeat_trade.scope: only 'recipe' is supported");
            }
            repeat = new AntiAutomationConfig.VillagerRepeatTrade(cooldown);
        }

        AntiAutomationConfig.BreedNonPlayerBreeder breeder = parseZeroEnum(
                section.getString("breed_non_player_breeder"),
                path + ".breed_non_player_breeder",
                AntiAutomationConfig.BreedNonPlayerBreeder.ZERO
        );

        return new AntiAutomationConfig(spawner, unplanted, recentlyPlaced, autoFed, repeat, breeder);
    }

    private <T extends Enum<T>> T parseZeroEnum(String value, String path, T zeroValue) {
        if (value == null) return null;
        if ("zero".equalsIgnoreCase(value)) return zeroValue;
        throw new YamlParseException(path + ": only 'zero' is supported");
    }
}
