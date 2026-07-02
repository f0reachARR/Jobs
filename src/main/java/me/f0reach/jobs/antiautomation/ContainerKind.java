package me.f0reach.jobs.antiautomation;

/**
 * auto_fed_processing で追跡する容器種別。
 * spec/02-yaml-schema.md 「anti_automation.auto_fed_processing」で
 * 「対象容器はプラグイン側で固定リスト (4 種) を持つ」と明記されている値集合。
 */
public enum ContainerKind {
    FURNACE("furnace"),
    BLAST_FURNACE("blast_furnace"),
    SMOKER("smoker"),
    BREWING_STAND("brewing_stand");

    private final String tag;

    ContainerKind(String tag) {
        this.tag = tag;
    }

    /** KVS key に埋め込む文字列。 */
    public String tag() {
        return tag;
    }
}
