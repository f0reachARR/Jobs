package me.f0reach.jobs.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * lang/*.yml をロードして locale -> key -> raw MiniMessage string の Map を保持する。
 *
 * jar 内の resources/lang/*.yml と plugins/Jobs/lang/*.yml の両方を読み、
 * 後者を優先する。ja_jp は必須。
 */
public final class LocaleRegistry {
    public static final String DEFAULT_LOCALE = "ja_jp";
    private static final String[] BUNDLED_LOCALES = {"ja_jp", "en_us"};

    private final Plugin plugin;
    private final Map<String, Map<String, String>> locales = new LinkedHashMap<>();

    public LocaleRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        locales.clear();
        // まず jar 同梱のロケールを読む。
        for (String locale : BUNDLED_LOCALES) {
            Map<String, String> loaded = loadBundled(locale);
            if (loaded != null) {
                locales.put(locale, loaded);
            }
        }
        // plugins/Jobs/lang/*.yml で上書き（存在すれば）
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (langDir.isDirectory()) {
            File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    String locale = name.substring(0, name.length() - ".yml".length())
                            .toLowerCase(Locale.ROOT);
                    Map<String, String> overrides = loadFile(file);
                    locales.merge(locale, overrides, (existing, ov) -> {
                        Map<String, String> merged = new LinkedHashMap<>(existing);
                        merged.putAll(ov);
                        return merged;
                    });
                }
            }
        }
        if (!locales.containsKey(DEFAULT_LOCALE)) {
            throw new IllegalStateException(
                    "Required locale '" + DEFAULT_LOCALE + "' is missing"
            );
        }
    }

    private Map<String, String> loadBundled(String locale) {
        String path = "lang/" + locale + ".yml";
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                return flatten(yaml);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read bundled locale " + locale, e);
            return null;
        }
    }

    private Map<String, String> loadFile(File file) {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            return flatten(yaml);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read locale file " + file, e);
            return Map.of();
        }
    }

    /**
     * ネストした ConfigurationSection をドット区切りのフラットな map に潰す。
     * 値は文字列化する（他型は toString）。
     */
    private Map<String, String> flatten(YamlConfiguration yaml) {
        Map<String, String> flat = new LinkedHashMap<>();
        for (String key : yaml.getKeys(true)) {
            Object value = yaml.get(key);
            if (value instanceof String s) {
                flat.put(key, s);
            } else if (value != null && !(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                flat.put(key, value.toString());
            }
        }
        return flat;
    }

    public String get(String locale, String key) {
        Map<String, String> lookup = locales.get(normalize(locale));
        if (lookup != null) {
            String value = lookup.get(key);
            if (value != null) return value;
        }
        Map<String, String> fallback = locales.get(DEFAULT_LOCALE);
        if (fallback != null) {
            String value = fallback.get(key);
            if (value != null) return value;
        }
        return key;
    }

    public boolean hasKey(String locale, String key) {
        Map<String, String> lookup = locales.get(normalize(locale));
        return lookup != null && lookup.containsKey(key);
    }

    public Set<String> keys(String locale) {
        Map<String, String> lookup = locales.get(normalize(locale));
        return lookup == null ? Set.of() : new LinkedHashSet<>(lookup.keySet());
    }

    public Set<String> locales() {
        return new LinkedHashSet<>(locales.keySet());
    }

    private static String normalize(String locale) {
        if (locale == null) return DEFAULT_LOCALE;
        return locale.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
