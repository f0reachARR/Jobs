package me.f0reach.jobs.registry;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * `#minecraft:undead` などのタグを {@code Set<NamespacedKey>} に resolve する。
 *
 * Paper の Registry ベース API を経由し、Vanilla の tag だけでなくデータパック
 * (`data/<ns>/tags/...`) も透過的に扱える。
 *
 * threading: LifecycleEvents.SERVER_LOAD で 1 度 warm up し、matcher からの
 * lookup は同期で行う。data pack reload 時は {@link #invalidateAll()} で cache を捨てる。
 */
public final class TagResolver {

    /** レジストリの種類。tag の対象領域ごとに分ける。 */
    public enum Kind {
        BLOCKS(Tag.REGISTRY_BLOCKS, Material.class),
        ITEMS(Tag.REGISTRY_ITEMS, Material.class),
        ENTITY_TYPES(Tag.REGISTRY_ENTITY_TYPES, EntityType.class);

        private final String registryName;
        private final Class<? extends Keyed> clazz;

        Kind(String registryName, Class<? extends Keyed> clazz) {
            this.registryName = registryName;
            this.clazz = clazz;
        }

        public String registryName() { return registryName; }
        public Class<? extends Keyed> clazz() { return clazz; }
    }

    private final Map<CacheKey, Set<NamespacedKey>> cache = new ConcurrentHashMap<>();

    private record CacheKey(Kind kind, NamespacedKey tagKey) {}

    /**
     * 指定の kind でタグを resolve する。存在しないタグは空集合を返す（呼び出し側で警告）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Set<NamespacedKey> resolve(Kind kind, NamespacedKey tagKey) {
        return cache.computeIfAbsent(new CacheKey(kind, tagKey), key -> {
            Tag tag = Bukkit.getTag(key.kind().registryName(), key.tagKey(), (Class) key.kind().clazz());
            if (tag == null) return Set.of();
            Set<? extends Keyed> values = tag.getValues();
            return values.stream().map(Keyed::getKey).collect(Collectors.toUnmodifiableSet());
        });
    }

    public void invalidateAll() {
        cache.clear();
    }

    /**
     * KeyMatcher.Tag からタグ内容を得るための関数を作る。
     * ActionType ごとに Kind が定まるため、matcher 側で kind を選んで渡す。
     */
    public java.util.function.Function<NamespacedKey, Set<NamespacedKey>> lookupFunction(Kind kind) {
        return tagKey -> resolve(kind, tagKey);
    }

    /** サーバー起動完了時に主要な Kind を warm up し、失敗 tag を早めに検出する。 */
    public void warmUp(Map<Kind, java.util.Collection<NamespacedKey>> knownTags) {
        for (Map.Entry<Kind, java.util.Collection<NamespacedKey>> e : knownTags.entrySet()) {
            for (NamespacedKey key : e.getValue()) {
                resolve(e.getKey(), key);
            }
        }
    }

    /** 空 warm up 用。呼び出し元が特定 tag を宣言しない場合は空 Map で足りる。 */
    public void warmUp() {
        warmUp(new HashMap<>());
    }
}
