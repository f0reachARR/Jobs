package me.f0reach.jobs.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
