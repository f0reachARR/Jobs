package me.f0reach.jobs.specialty;

import me.f0reach.jobs.config.PluginConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * config.specialty_mode.change_policy を評価して現在の cooldown を決める。
 *
 * 上から評価し、within にマッチした最初のポリシーの cooldown を採用する。
 * default は条件なしのフォールバック。
 *
 * spec/02-yaml-schema.md 「グローバル設定 / specialty_mode」を参照。
 */
public final class CooldownPolicy {

    private final List<PluginConfig.ChangePolicy> policies;
    private final ZoneId zone;

    public CooldownPolicy(List<PluginConfig.ChangePolicy> policies) {
        this(policies, ZoneId.systemDefault());
    }

    public CooldownPolicy(List<PluginConfig.ChangePolicy> policies, ZoneId zone) {
        this.policies = policies;
        this.zone = zone;
    }

    /**
     * 現時点で適用される cooldown を返す。
     * どれにもマッチしない場合は Duration.ZERO（＝いつでも変更可）を返す。
     */
    public Duration currentCooldown(Instant now) {
        int hour = LocalDateTime.ofInstant(now, zone).getHour();
        for (PluginConfig.ChangePolicy policy : policies) {
            if (policy.isDefault()) {
                return policy.cooldown();
            }
            if (matches(policy.within(), hour)) {
                return policy.cooldown();
            }
        }
        return Duration.ZERO;
    }

    private boolean matches(PluginConfig.WithinCondition within, int hour) {
        if (within == null) return false;
        List<Integer> hours = within.eventHours();
        if (hours == null || hours.isEmpty()) return false;
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
}
