package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.matcher.KeyMatcher;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

/**
 * "minecraft:zombie" のような単一 ID、リスト、"#minecraft:undead" のようなタグを
 * {@link KeyMatcher} に落とし込む。
 */
public final class KeyMatcherParser {

    public KeyMatcher parse(Object raw, String path) {
        if (raw == null) {
            throw new YamlParseException("Missing value at " + path);
        }
        if (raw instanceof List<?> list) {
            if (list.isEmpty()) {
                throw new YamlParseException(path + ": list must not be empty");
            }
            // タグを混在させると意味論が曖昧になるので、リスト要素は単一 ID のみ許可する。
            List<NamespacedKey> parsed = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String s)) {
                    throw new YamlParseException(path + ": list element must be string");
                }
                if (s.startsWith("#")) {
                    throw new YamlParseException(
                            path + ": tag (#..) cannot appear inside a list — use single tag form"
                    );
                }
                parsed.add(parseKey(s, path));
            }
            if (parsed.size() == 1) {
                return new KeyMatcher.Single(parsed.get(0));
            }
            return new KeyMatcher.ListOf(parsed);
        }
        if (raw instanceof String s) {
            if (s.startsWith("#")) {
                return new KeyMatcher.Tag(parseKey(s.substring(1), path));
            }
            return new KeyMatcher.Single(parseKey(s, path));
        }
        throw new YamlParseException(
                path + ": expected string, list, or tag but got " + raw.getClass().getSimpleName()
        );
    }

    private NamespacedKey parseKey(String value, String path) {
        NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(java.util.Locale.ROOT));
        if (key == null) {
            throw new YamlParseException(path + ": invalid NamespacedKey '" + value + "'");
        }
        return key;
    }
}
