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
                new PluginConfig.WithinCondition(List.of(start, end)),
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
        assertEquals(Duration.ofDays(5), p.currentCooldown(atHour(10)));
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
        assertEquals(Duration.ofHours(1), p.currentCooldown(atHour(10)));
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
        assertEquals(Duration.ofMinutes(30), p.currentCooldown(atHour(15)));
        assertEquals(Duration.ofDays(1), p.currentCooldown(atHour(11)));
        assertEquals(Duration.ofDays(1), p.currentCooldown(atHour(18))); // 排他上限
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
        assertEquals(Duration.ofMinutes(10), p.currentCooldown(atHour(23)));
        assertEquals(Duration.ofMinutes(10), p.currentCooldown(atHour(3)));
        assertEquals(Duration.ofHours(1), p.currentCooldown(atHour(6)));
        assertEquals(Duration.ofHours(1), p.currentCooldown(atHour(12)));
    }

    @Test
    void emptyReturnsZero() {
        CooldownPolicy p = new CooldownPolicy(List.of(), ZoneOffset.UTC);
        assertEquals(Duration.ZERO, p.currentCooldown(atHour(10)));
    }
}
