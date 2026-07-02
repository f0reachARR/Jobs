package me.f0reach.jobs.kvs;

import java.util.UUID;

/**
 * KVS の key 文字列を集約する。
 * 呼び出し側で key を組み立てるとバラつきが出るため、この class に閉じる。
 *
 * 詳細は spec/05-persistence.md 「追跡ストレージ (KVS) / interface」を参照。
 */
public final class KvsKeys {

    private KvsKeys() {}

    /**
     * recently_placed_break 用。ブロック 1 個ごとにキー。
     */
    public static String place(UUID worldUuid, int x, int y, int z) {
        return "place:" + worldUuid + ":" + x + ":" + y + ":" + z;
    }

    /**
     * auto_fed_processing 用。容器 1 個ごとにキー。
     */
    public static String op(String containerKind, UUID worldUuid, int x, int y, int z) {
        return "op:" + containerKind + ":" + worldUuid + ":" + x + ":" + y + ":" + z;
    }

    /**
     * villager_repeat_trade 用。同 villager × 同 recipe ごとにキー。
     */
    public static String trade(UUID villagerUuid, int recipeIndex) {
        return "trade:" + villagerUuid + ":" + recipeIndex;
    }
}
