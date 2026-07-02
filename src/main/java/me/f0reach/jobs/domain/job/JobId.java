package me.f0reach.jobs.domain.job;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * ジョブの一意識別子。ASCII 小文字とアンダースコアのみで構成する。
 * 変更すると行動ログとの紐付けが切れるため、運用開始後は固定する。
 */
public record JobId(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_]+$");

    public JobId {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid job id: '" + value + "' (allowed: [a-z0-9_])"
            );
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
