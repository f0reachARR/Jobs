package me.f0reach.jobs.domain.job;

import org.bukkit.World;

/**
 * entity_killed の dimension フィルタで使う discrete な dimension 種別。
 * Bukkit の {@link World.Environment} を Vanilla dimension に写像する。
 * CUSTOM 環境（datapack 等の追加 dimension）は {@code null} を返し、非マッチとして扱う。
 */
public enum Dimension {
    OVERWORLD,
    NETHER,
    END;

    public static Dimension fromEnvironment(World.Environment env) {
        if (env == null) return null;
        return switch (env) {
            case NORMAL -> OVERWORLD;
            case NETHER -> NETHER;
            case THE_END -> END;
            case CUSTOM -> null;
        };
    }
}
