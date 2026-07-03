package me.f0reach.jobs.ui;

import me.f0reach.jobs.domain.job.ActionType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DialogTexts の各 key が同梱 lang ファイル (ja_jp / en_us) に存在することを保証する。
 * DIALOG_INFO_REWARD_LABEL_PREFIX は prefix なので、全 ActionType 分の suffix を組み立てて確認する。
 */
class DialogTextsIntegrityTest {

    private Map<String, String> loadLang(String locale) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + locale + ".yml")) {
            assertNotNull(in, "resource missing: lang/" + locale + ".yml");
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                Map<String, String> flat = new LinkedHashMap<>();
                for (String key : yaml.getKeys(true)) {
                    Object value = yaml.get(key);
                    if (value instanceof String s) flat.put(key, s);
                }
                return flat;
            }
        }
    }

    private List<String> allKeysFromDialogTexts() throws Exception {
        List<String> keys = new java.util.ArrayList<>();
        for (Field f : DialogTexts.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!f.getType().equals(String.class)) continue;
            if (f.getName().endsWith("_PREFIX")) continue; // suffix を後で足す
            keys.add((String) f.get(null));
        }
        // reward label prefix + action_type suffix
        for (ActionType at : ActionType.values()) {
            keys.add(DialogTexts.DIALOG_INFO_REWARD_LABEL_PREFIX + at.name().toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    @Test
    void jaJpContainsEveryDialogTextsKey() throws Exception {
        Map<String, String> ja = loadLang("ja_jp");
        Set<String> missing = new TreeSet<>();
        for (String key : allKeysFromDialogTexts()) {
            if (!ja.containsKey(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(), () -> "ja_jp missing keys: " + missing);
    }

    @Test
    void enUsContainsEveryDialogTextsKey() throws Exception {
        Map<String, String> en = loadLang("en_us");
        Set<String> missing = new TreeSet<>();
        for (String key : allKeysFromDialogTexts()) {
            if (!en.containsKey(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(), () -> "en_us missing keys: " + missing);
    }
}
