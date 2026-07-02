package me.f0reach.jobs.domain.job;

import java.util.Objects;

/**
 * MatchCriteria から決定的に生成される派生キー。
 * action_log の action_key カラムと単調性ペナルティのバケット識別子を兼ねる。
 * 生成規則は {@code registry.ActionKeyDeriver} に閉じる。
 */
public record ActionKey(String value) {
    public ActionKey {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("ActionKey must not be empty");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
