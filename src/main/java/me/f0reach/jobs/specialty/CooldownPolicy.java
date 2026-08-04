package me.f0reach.jobs.specialty;

import me.f0reach.jobs.config.PluginConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

/**
 * config.specialty_mode.change_policy を評価して現在の cooldown を決める。
 *
 * 上から評価し、within にマッチした最初のポリシーの cooldown を採用する。
 * default は条件なしのフォールバック。
 *
 * spec/02-yaml-schema.md 「グローバル設定 / specialty_mode」を参照。
 */
public final class CooldownPolicy {

    private final Supplier<List<PluginConfig.ChangePolicy>> policies;
    private final ZoneId zone;

    /** 固定のポリシーで組む。テストなど reload を伴わない用途向け。 */
    public CooldownPolicy(List<PluginConfig.ChangePolicy> policies) {
        this(policies, ZoneId.systemDefault());
    }

    /** 固定のポリシーで組む。テストなど reload を伴わない用途向け。 */
    public CooldownPolicy(List<PluginConfig.ChangePolicy> policies, ZoneId zone) {
        this(() -> policies, zone);
    }

    /**
     * config を参照して組む。{@code /jobs reload} で change_policy が差し替わるので、
     * 判定のたびに supplier から読み直す。
     */
    public CooldownPolicy(Supplier<List<PluginConfig.ChangePolicy>> policies, ZoneId zone) {
        this.policies = policies;
        this.zone = zone;
    }

    /**
     * 現時点で適用される cooldown を返す。
     * どれにもマッチしない場合は Duration.ZERO（＝いつでも変更可）を返す。
     *
     * @param firstJoinAt サーバー初参加時刻。不明なら null。null のとき
     *                    {@code first_join_within} を持つポリシーはマッチしない。
     */
    public Duration currentCooldown(Instant now, Instant firstJoinAt) {
        int hour = LocalDateTime.ofInstant(now, zone).getHour();
        for (PluginConfig.ChangePolicy policy : policies.get()) {
            if (policy.isDefault()) {
                return policy.cooldown();
            }
            if (matches(policy.within(), now, hour, firstJoinAt)) {
                return policy.cooldown();
            }
        }
        return Duration.ZERO;
    }

    /** within に書かれたキーをすべて満たしたときだけマッチする（AND）。 */
    private boolean matches(
            PluginConfig.WithinCondition within, Instant now, int hour, Instant firstJoinAt
    ) {
        if (within == null || within.isEmpty()) return false;
        if (!within.eventHours().isEmpty() && !matchesEventHours(within.eventHours(), hour)) {
            return false;
        }
        return within.firstJoinWithin() == null
                || matchesFirstJoin(within.firstJoinWithin(), now, firstJoinAt);
    }

    private boolean matchesEventHours(List<Integer> hours, int hour) {
        // 現状の spec では [start, end] の 2 要素リスト。end は排他上限。
        if (hours.size() == 2) {
            int start = hours.get(0);
            int end = hours.get(1);
            if (start <= end) return hour >= start && hour < end;
            // 深夜跨ぎ（例: [22, 6]）
            return hour >= start || hour < end;
        }
        return hours.contains(hour);
    }

    /** 初参加からの経過が limit 未満か。初参加時刻が不明ならマッチさせない。 */
    private boolean matchesFirstJoin(Duration limit, Instant now, Instant firstJoinAt) {
        if (firstJoinAt == null) return false;
        return now.isBefore(firstJoinAt.plus(limit));
    }
}
