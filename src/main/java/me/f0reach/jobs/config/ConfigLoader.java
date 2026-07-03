package me.f0reach.jobs.config;

import me.f0reach.jobs.yaml.AntiAutomationParser;
import me.f0reach.jobs.yaml.YamlParseException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * plugin.getConfig() から {@link PluginConfig} を組み立てる。
 * 不正値は {@link ConfigException} を投げ、onEnable は起動失敗にする。
 */
public final class ConfigLoader {

    public PluginConfig load(FileConfiguration config) {
        return new PluginConfig(
                loadSpecialtyMode(config.getConfigurationSection("specialty_mode")),
                loadReward(config.getConfigurationSection("reward")),
                loadDailyCap(config.getConfigurationSection("daily_cap")),
                loadPersistence(config.getConfigurationSection("persistence")),
                loadKvs(config.getConfigurationSection("kvs")),
                loadAntiAutomation(config.getConfigurationSection("anti_automation"))
        );
    }

    private PluginConfig.RewardConfig loadReward(ConfigurationSection section) {
        if (section == null) {
            return new PluginConfig.RewardConfig(0, RoundingMode.HALF_UP);
        }
        int decimals = section.getInt("decimals", 0);
        String modeRaw = section.getString("rounding_mode", "HALF_UP").toUpperCase(Locale.ROOT);
        RoundingMode mode;
        try {
            mode = RoundingMode.valueOf(modeRaw);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Unknown reward.rounding_mode: " + modeRaw, e);
        }
        try {
            return new PluginConfig.RewardConfig(decimals, mode);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e.getMessage(), e);
        }
    }

    private PluginConfig.SpecialtyModeConfig loadSpecialtyMode(ConfigurationSection section) {
        if (section == null) {
            throw new ConfigException("specialty_mode section is missing in config.yml");
        }
        double reward = section.getDouble("reward_non_specialty", 0.0);
        boolean showSelectDialogOnJoin = section.getBoolean("show_select_dialog_on_join", true);
        boolean discloseBeforeSelect = section.getBoolean("disclose_before_select", true);
        boolean discloseRewardAmount = section.getBoolean("disclose_reward_amount", true);
        List<PluginConfig.ChangePolicy> policies = new ArrayList<>();
        List<?> raw = section.getList("change_policy");
        if (raw != null) {
            for (Object entry : raw) {
                if (!(entry instanceof java.util.Map<?, ?> map)) {
                    throw new ConfigException("change_policy entry must be a map: " + entry);
                }
                policies.add(parseChangePolicy(map));
            }
        }
        return new PluginConfig.SpecialtyModeConfig(
                reward,
                showSelectDialogOnJoin,
                discloseBeforeSelect,
                discloseRewardAmount,
                List.copyOf(policies)
        );
    }

    private PluginConfig.ChangePolicy parseChangePolicy(java.util.Map<?, ?> map) {
        Object cooldownRaw = map.get("cooldown");
        if (cooldownRaw == null) {
            throw new ConfigException("change_policy entry missing 'cooldown': " + map);
        }
        Duration cooldown = parseDuration(cooldownRaw.toString());

        boolean isDefault = map.containsKey("default");
        PluginConfig.WithinCondition within = PluginConfig.WithinCondition.none();
        Object withinRaw = map.get("within");
        if (withinRaw instanceof java.util.Map<?, ?> withinMap) {
            Object hours = withinMap.get("event_hours");
            if (hours instanceof List<?> list) {
                List<Integer> parsed = new ArrayList<>();
                for (Object h : list) {
                    if (h instanceof Number n) {
                        parsed.add(n.intValue());
                    } else {
                        throw new ConfigException("event_hours must be numbers: " + hours);
                    }
                }
                within = new PluginConfig.WithinCondition(List.copyOf(parsed));
            }
        }
        return new PluginConfig.ChangePolicy(isDefault, within, cooldown);
    }

    private Duration parseDuration(String value) {
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            throw new ConfigException("Empty duration");
        }
        char unit = trimmed.charAt(trimmed.length() - 1);
        String number = trimmed.substring(0, trimmed.length() - 1);
        try {
            long n = Long.parseLong(number);
            return switch (unit) {
                case 's' -> Duration.ofSeconds(n);
                case 'm' -> Duration.ofMinutes(n);
                case 'h' -> Duration.ofHours(n);
                case 'd' -> Duration.ofDays(n);
                default -> throw new ConfigException("Unknown duration unit: " + unit);
            };
        } catch (NumberFormatException e) {
            throw new ConfigException("Invalid duration: " + value, e);
        }
    }

    private PluginConfig.DailyCapConfig loadDailyCap(ConfigurationSection section) {
        if (section == null) {
            throw new ConfigException("daily_cap section is missing in config.yml");
        }
        long amount = section.getLong("amount", 0);
        if (amount < 0) {
            throw new ConfigException("daily_cap.amount must be non-negative");
        }
        String resetAt = section.getString("reset_at", "00:00");
        String scopeRaw = section.getString("scope", "total").toUpperCase(Locale.ROOT);
        PluginConfig.DailyCapConfig.Scope scope;
        try {
            scope = PluginConfig.DailyCapConfig.Scope.valueOf(scopeRaw);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Unknown daily_cap.scope: " + scopeRaw, e);
        }
        return new PluginConfig.DailyCapConfig(amount, resetAt, scope);
    }

    private PluginConfig.PersistenceConfig loadPersistence(ConfigurationSection section) {
        if (section == null) {
            throw new ConfigException("persistence section is missing in config.yml");
        }
        String typeRaw = section.getString("type", "mysql").toUpperCase(Locale.ROOT);
        PluginConfig.PersistenceConfig.Type type;
        try {
            type = PluginConfig.PersistenceConfig.Type.valueOf(typeRaw);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Unknown persistence.type: " + typeRaw, e);
        }
        return new PluginConfig.PersistenceConfig(
                type,
                section.getString("host", "localhost"),
                section.getInt("port", 3306),
                section.getString("database", "jobs"),
                section.getString("user", "jobs"),
                section.getString("password", ""),
                section.getInt("pool_size", 8),
                section.getInt("retention_days", 30)
        );
    }

    private PluginConfig.AntiAutomationConfig loadAntiAutomation(ConfigurationSection section) {
        if (section == null) {
            return PluginConfig.AntiAutomationConfig.empty();
        }
        // per-job と同じ parser でデフォルト値を読む。notify は parser 側では見ない。
        me.f0reach.jobs.domain.job.AntiAutomationConfig defaults;
        try {
            defaults = new AntiAutomationParser().parse(section, "anti_automation");
        } catch (YamlParseException e) {
            throw new ConfigException("Invalid anti_automation defaults: " + e.getMessage(), e);
        }
        Map<String, Boolean> notify = loadNotifyActionBar(
                section.getConfigurationSection("notify"));
        return new PluginConfig.AntiAutomationConfig(defaults, notify);
    }

    private Map<String, Boolean> loadNotifyActionBar(ConfigurationSection notifySection) {
        if (notifySection == null) return Map.of();
        ConfigurationSection actionBar = notifySection.getConfigurationSection("action_bar");
        if (actionBar == null) return Map.of();
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String key : actionBar.getKeys(false)) {
            map.put(key, actionBar.getBoolean(key, false));
        }
        return map;
    }

    private PluginConfig.KvsConfig loadKvs(ConfigurationSection section) {
        if (section == null) {
            return new PluginConfig.KvsConfig(PluginConfig.KvsConfig.Type.MEMORY);
        }
        String typeRaw = section.getString("type", "memory").toUpperCase(Locale.ROOT);
        PluginConfig.KvsConfig.Type type;
        try {
            type = PluginConfig.KvsConfig.Type.valueOf(typeRaw);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Unknown kvs.type: " + typeRaw, e);
        }
        return new PluginConfig.KvsConfig(type);
    }
}
