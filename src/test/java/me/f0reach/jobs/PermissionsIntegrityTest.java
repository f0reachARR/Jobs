package me.f0reach.jobs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Permissions} の各定数が paper-plugin.yml の permissions セクションに宣言されて
 * いることを検証する。DialogTextsIntegrityTest と同じ発想の drift 検出。
 */
class PermissionsIntegrityTest {

    private Set<String> loadDeclaredPermissions() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("paper-plugin.yml")) {
            assertNotNull(in, "resource missing: paper-plugin.yml");
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = new YamlConfiguration();
                // permission ノード名に "." が含まれるので、YamlConfiguration の path separator を
                // 無効化してから load する。default の '.' のままだと "jobs.command.use" が
                // ネストとして解釈され、getKeys で拾えなくなる。
                yaml.options().pathSeparator('/');
                yaml.load(reader);
                var section = yaml.getConfigurationSection("permissions");
                assertNotNull(section, "paper-plugin.yml has no permissions section");
                return new TreeSet<>(section.getKeys(false));
            }
        }
    }

    private List<String> allPermissionConstants() throws Exception {
        List<String> keys = new ArrayList<>();
        for (Field f : Permissions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!f.getType().equals(String.class)) continue;
            keys.add((String) f.get(null));
        }
        return keys;
    }

    @Test
    void paperPluginYmlDeclaresEveryConstant() throws Exception {
        Set<String> declared = loadDeclaredPermissions();
        Set<String> missing = new TreeSet<>();
        for (String key : allPermissionConstants()) {
            if (!declared.contains(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(),
                () -> "paper-plugin.yml is missing permission declarations: " + missing);
    }
}
