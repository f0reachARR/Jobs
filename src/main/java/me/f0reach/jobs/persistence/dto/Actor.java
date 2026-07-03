package me.f0reach.jobs.persistence.dto;

/**
 * player_job_history.actor に対応する enum。
 * spec/05-persistence.md 「player_job_history」を参照。
 */
public enum Actor {
    /** プレイヤー本人による /jobs select / change。 */
    PLAYER,
    /** /jobs admin set による強制付与。 */
    ADMIN,
    /** 将来的な自動付与用の枠。現時点では使わない。 */
    SYSTEM;

    /** DDL 側の小文字表現に揃える。 */
    public String dbValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Actor fromDb(String value) {
        return Actor.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}
