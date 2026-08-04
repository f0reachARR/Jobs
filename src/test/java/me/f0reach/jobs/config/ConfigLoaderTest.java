package me.f0reach.jobs.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    private final ConfigLoader loader = new ConfigLoader();

    private PluginConfig load(String yaml) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        return loader.load(cfg);
    }

    @Test
    void specialtyModeDefaultsWhenSectionOnlyHasRequiredKeys() {
        PluginConfig config = load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  change_policy:
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 0
                  rounding_mode: HALF_UP
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """);
        PluginConfig.SpecialtyModeConfig mode = config.specialtyMode();
        assertTrue(mode.showSelectDialogOnJoin(), "default true");
        assertTrue(mode.discloseBeforeSelect(), "default true");
        assertTrue(mode.discloseRewardAmount(), "default true");
    }

    @Test
    void specialtyModeOverridesFlags() {
        PluginConfig config = load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  show_select_dialog_on_join: false
                  disclose_before_select: false
                  disclose_reward_amount: false
                  change_policy:
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 0
                  rounding_mode: HALF_UP
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """);
        PluginConfig.SpecialtyModeConfig mode = config.specialtyMode();
        assertFalse(mode.showSelectDialogOnJoin());
        assertFalse(mode.discloseBeforeSelect());
        assertFalse(mode.discloseRewardAmount());
    }

    @Test
    void changePolicyParsesFirstJoinWithin() {
        PluginConfig config = load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  change_policy:
                    - within:
                        first_join_within: 72h
                      cooldown: 1h
                    - within:
                        event_hours: [20, 23]
                        first_join_within: 30d
                      cooldown: 6h
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 0
                  rounding_mode: HALF_UP
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """);
        var policies = config.specialtyMode().changePolicy();
        assertEquals(3, policies.size());

        PluginConfig.WithinCondition first = policies.get(0).within();
        assertEquals(java.time.Duration.ofHours(72), first.firstJoinWithin());
        assertTrue(first.eventHours().isEmpty());
        assertFalse(first.isEmpty());

        PluginConfig.WithinCondition second = policies.get(1).within();
        assertEquals(java.time.Duration.ofDays(30), second.firstJoinWithin());
        assertEquals(java.util.List.of(20, 23), second.eventHours());

        // default は within なし
        assertTrue(policies.get(2).within().isEmpty());
        assertNull(policies.get(2).within().firstJoinWithin());
    }

    @Test
    void changePolicyRejectsNonPositiveFirstJoinWithin() {
        assertThrows(ConfigException.class, () -> load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  change_policy:
                    - within:
                        first_join_within: 0h
                      cooldown: 1h
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 0
                  rounding_mode: HALF_UP
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """));
    }

    @Test
    void rewardDecimalsPropagated() {
        PluginConfig config = load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  change_policy:
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 2
                  rounding_mode: HALF_EVEN
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """);
        assertEquals(2, config.reward().decimals());
        assertEquals(java.math.RoundingMode.HALF_EVEN, config.reward().roundingMode());
    }

    @Test
    void rewardAsyncDefaultsWhenSectionMissing() {
        PluginConfig config = load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  change_policy:
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 0
                  rounding_mode: HALF_UP
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """);
        PluginConfig.AsyncConfig async = config.reward().async();
        assertEquals(PluginConfig.AsyncConfig.defaults(), async);
        assertTrue(async.enabled());
        assertEquals(100_000, async.queueCapacity());
        assertFalse(async.economyOnMain());
    }

    @Test
    void rewardAsyncOverridesEveryKey() {
        PluginConfig config = load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  change_policy:
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 0
                  rounding_mode: HALF_UP
                  async:
                    enabled: false
                    queue_capacity: 512
                    backlog_warn_ratio: [0.25, 0.9]
                    economy_on_main: true
                    main_work_per_tick: 32
                    slow_extension_threshold_ms: 10
                    drain_timeout_ms: 1234
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """);
        PluginConfig.AsyncConfig async = config.reward().async();
        assertFalse(async.enabled());
        assertEquals(512, async.queueCapacity());
        assertEquals(java.util.List.of(0.25, 0.9), async.backlogWarnRatios());
        assertTrue(async.economyOnMain());
        assertEquals(32, async.mainWorkPerTick());
        assertEquals(10L, async.slowExtensionThresholdMs());
        assertEquals(1234L, async.drainTimeoutMs());
    }

    @Test
    void rewardAsyncRejectsInvalidQueueCapacity() {
        org.junit.jupiter.api.Assertions.assertThrows(ConfigException.class, () -> load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  change_policy:
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 0
                  rounding_mode: HALF_UP
                  async:
                    queue_capacity: 0
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """));
    }

    @Test
    void rewardAsyncRejectsOutOfRangeBacklogRatio() {
        org.junit.jupiter.api.Assertions.assertThrows(ConfigException.class, () -> load("""
                specialty_mode:
                  reward_non_specialty: 0.0
                  change_policy:
                    - default:
                      cooldown: 5d
                reward:
                  decimals: 0
                  rounding_mode: HALF_UP
                  async:
                    backlog_warn_ratio: [1.5]
                daily_cap:
                  amount: 0
                  scope: total
                persistence:
                  type: mysql
                kvs:
                  type: memory
                """));
    }
}
