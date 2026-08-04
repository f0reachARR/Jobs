package me.f0reach.jobs.testsupport;

import me.f0reach.jobs.specialty.FirstJoinProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** すべてのプレイヤーに同じ初参加時刻を返す {@link FirstJoinProvider}。 */
public final class FixedFirstJoinProvider implements FirstJoinProvider {

    private final Instant firstJoinAt;

    private FixedFirstJoinProvider(Instant firstJoinAt) {
        this.firstJoinAt = firstJoinAt;
    }

    /** 初参加時刻が取得できないサーバーを模す。 */
    public static FixedFirstJoinProvider unknown() {
        return new FixedFirstJoinProvider(null);
    }

    public static FixedFirstJoinProvider at(Instant firstJoinAt) {
        return new FixedFirstJoinProvider(firstJoinAt);
    }

    @Override
    public Optional<Instant> firstJoinAt(UUID player) {
        return Optional.ofNullable(firstJoinAt);
    }
}
