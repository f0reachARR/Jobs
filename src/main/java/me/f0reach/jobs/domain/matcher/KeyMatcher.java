package me.f0reach.jobs.domain.matcher;

import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 「単一の Key」「Key のリスト」「タグ」いずれかを統一的に扱う value type。
 *
 * spec/02-yaml-schema.md 「match フィールド」に従い、
 * 値、リスト、タグはいずれも OR とみなす。
 *
 * matches の実装で参照するタグ集合は外部から与える（TagResolver 経由）。
 * イミュータブルで equals/hashCode は record が生成する。
 */
public sealed interface KeyMatcher {

    /** マッチするか。tag ケースでは lookupTag に集合を渡す。 */
    boolean matches(NamespacedKey target, java.util.function.Function<NamespacedKey, Set<NamespacedKey>> tagLookup);

    /** 派生キーの表現。ActionKeyDeriver から呼ばれる。 */
    String toDerivedKey();

    /** 単一 key での指定。 */
    record Single(NamespacedKey key) implements KeyMatcher {
        public Single {
            Objects.requireNonNull(key, "key");
        }

        @Override
        public boolean matches(NamespacedKey target, java.util.function.Function<NamespacedKey, Set<NamespacedKey>> tagLookup) {
            return key.equals(target);
        }

        @Override
        public String toDerivedKey() {
            return key.toString();
        }
    }

    /** リストでの指定。順序は派生キーで一意にするため sort する。 */
    record ListOf(List<NamespacedKey> keys) implements KeyMatcher {
        public ListOf {
            Objects.requireNonNull(keys, "keys");
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("ListOf must not be empty");
            }
            keys = List.copyOf(keys);
        }

        @Override
        public boolean matches(NamespacedKey target, java.util.function.Function<NamespacedKey, Set<NamespacedKey>> tagLookup) {
            for (NamespacedKey k : keys) {
                if (k.equals(target)) return true;
            }
            return false;
        }

        @Override
        public String toDerivedKey() {
            // 各要素を文字列化してソートし、[a|b|c] に整形する。
            TreeSet<String> sorted = new TreeSet<>();
            for (NamespacedKey k : keys) sorted.add(k.toString());
            return "[" + String.join("|", sorted) + "]";
        }
    }

    /** タグでの指定。 tag は `#namespace:tag_name` の形式。 */
    record Tag(NamespacedKey tag) implements KeyMatcher {
        public Tag {
            Objects.requireNonNull(tag, "tag");
        }

        @Override
        public boolean matches(NamespacedKey target, java.util.function.Function<NamespacedKey, Set<NamespacedKey>> tagLookup) {
            Set<NamespacedKey> resolved = tagLookup.apply(tag);
            return resolved != null && resolved.contains(target);
        }

        @Override
        public String toDerivedKey() {
            return "#" + tag;
        }
    }
}
