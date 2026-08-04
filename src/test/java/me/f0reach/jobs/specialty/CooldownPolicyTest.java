package me.f0reach.jobs.specialty;

import me.f0reach.jobs.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CooldownPolicyTest {

    private static PluginConfig.ChangePolicy within(int start, int end, Duration cooldown) {
        return new PluginConfig.ChangePolicy(
                false,
                new PluginConfig.WithinCondition(List.of(start, end), null),
                cooldown
        );
    }

    private static PluginConfig.ChangePolicy firstJoinWithin(Duration limit, Duration cooldown) {
        return new PluginConfig.ChangePolicy(
                false,
                new PluginConfig.WithinCondition(List.of(), limit),
                cooldown
        );
    }

    private static PluginConfig.ChangePolicy hoursAndFirstJoin(
            int start, int end, Duration limit, Duration cooldown
    ) {
        return new PluginConfig.ChangePolicy(
                false,
                new PluginConfig.WithinCondition(List.of(start, end), limit),
                cooldown
        );
    }

    private static PluginConfig.ChangePolicy defaultPolicy(Duration cooldown) {
        return new PluginConfig.ChangePolicy(true, PluginConfig.WithinCondition.none(), cooldown);
    }

    private static Instant atHour(int hour) {
        return LocalDateTime.of(2026, 1, 1, hour, 0).toInstant(ZoneOffset.UTC);
    }

    @Test
    void defaultPolicyAppliesWhenNoMatch() {
        CooldownPolicy p = new CooldownPolicy(
                List.of(defaultPolicy(Duration.ofDays(5))),
                ZoneOffset.UTC.normalized() == ZoneOffset.UTC ? java.time.ZoneOffset.UTC : ZoneId.systemDefault()
        );
        assertEquals(Duration.ofDays(5), p.currentCooldown(atHour(10), null));
    }

    @Test
    void withinRangeMatchesFirstPolicy() {
        CooldownPolicy p = new CooldownPolicy(
                List.of(
                        within(0, 24, Duration.ofHours(1)),
                        defaultPolicy(Duration.ofDays(5))
                ),
                ZoneOffset.UTC
        );
        assertEquals(Duration.ofHours(1), p.currentCooldown(atHour(10), null));
    }

    @Test
    void narrowWithinRange() {
        CooldownPolicy p = new CooldownPolicy(
                List.of(
                        within(12, 18, Duration.ofMinutes(30)),
                        defaultPolicy(Duration.ofDays(1))
                ),
                ZoneOffset.UTC
        );
        assertEquals(Duration.ofMinutes(30), p.currentCooldown(atHour(15), null));
        assertEquals(Duration.ofDays(1), p.currentCooldown(atHour(11), null));
        assertEquals(Duration.ofDays(1), p.currentCooldown(atHour(18), null)); // 排他上限
    }

    @Test
    void wrappingRangeCoversMidnight() {
        // 22:00〜06:00
        CooldownPolicy p = new CooldownPolicy(
                List.of(
                        within(22, 6, Duration.ofMinutes(10)),
                        defaultPolicy(Duration.ofHours(1))
                ),
                ZoneOffset.UTC
        );
        assertEquals(Duration.ofMinutes(10), p.currentCooldown(atHour(23), null));
        assertEquals(Duration.ofMinutes(10), p.currentCooldown(atHour(3), null));
        assertEquals(Duration.ofHours(1), p.currentCooldown(atHour(6), null));
        assertEquals(Duration.ofHours(1), p.currentCooldown(atHour(12), null));
    }

    @Test
    void emptyReturnsZero() {
        CooldownPolicy p = new CooldownPolicy(List.of(), ZoneOffset.UTC);
        assertEquals(Duration.ZERO, p.currentCooldown(atHour(10), null));
    }

    @Test
    void firstJoinWithinMatchesNewPlayer() {
        CooldownPolicy p = new CooldownPolicy(
                List.of(
                        firstJoinWithin(Duration.ofHours(72), Duration.ofHours(1)),
                        defaultPolicy(Duration.ofDays(5))
                ),
                ZoneOffset.UTC
        );
        Instant now = atHour(10);
        assertEquals(Duration.ofHours(1),
                p.currentCooldown(now, now.minus(Duration.ofHours(71))));
    }

    @Test
    void firstJoinWithinFallsThroughAfterThreshold() {
        CooldownPolicy p = new CooldownPolicy(
                List.of(
                        firstJoinWithin(Duration.ofHours(72), Duration.ofHours(1)),
                        defaultPolicy(Duration.ofDays(5))
                ),
                ZoneOffset.UTC
        );
        Instant now = atHour(10);
        // 境界はちょうど 72h で切れる（排他上限）
        assertEquals(Duration.ofDays(5),
                p.currentCooldown(now, now.minus(Duration.ofHours(72))));
        assertEquals(Duration.ofDays(5),
                p.currentCooldown(now, now.minus(Duration.ofDays(30))));
    }

    @Test
    void firstJoinWithinDoesNotMatchWhenFirstJoinUnknown() {
        CooldownPolicy p = new CooldownPolicy(
                List.of(
                        firstJoinWithin(Duration.ofHours(72), Duration.ofHours(1)),
                        defaultPolicy(Duration.ofDays(5))
                ),
                ZoneOffset.UTC
        );
        assertEquals(Duration.ofDays(5), p.currentCooldown(atHour(10), null));
    }

    @Test
    void multipleWithinKeysAreAnded() {
        CooldownPolicy p = new CooldownPolicy(
                List.of(
                        hoursAndFirstJoin(20, 23, Duration.ofDays(30), Duration.ofHours(6)),
                        defaultPolicy(Duration.ofDays(5))
                ),
                ZoneOffset.UTC
        );
        Instant inEvent = atHour(21);
        Instant outOfEvent = atHour(10);
        Instant newbie = inEvent.minus(Duration.ofDays(1));
        Instant veteran = inEvent.minus(Duration.ofDays(60));

        assertEquals(Duration.ofHours(6), p.currentCooldown(inEvent, newbie));
        // 時間帯だけ外れてもマッチしない
        assertEquals(Duration.ofDays(5), p.currentCooldown(outOfEvent, newbie));
        // 初参加からの経過だけ外れてもマッチしない
        assertEquals(Duration.ofDays(5), p.currentCooldown(inEvent, veteran));
    }

    @Test
    void eventHoursOnlyPolicyIgnoresFirstJoin() {
        CooldownPolicy p = new CooldownPolicy(
                List.of(
                        within(0, 24, Duration.ofHours(1)),
                        defaultPolicy(Duration.ofDays(5))
                ),
                ZoneOffset.UTC
        );
        Instant now = atHour(10);
        assertEquals(Duration.ofHours(1), p.currentCooldown(now, now.minus(Duration.ofDays(365))));
    }
}
