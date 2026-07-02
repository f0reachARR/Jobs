package me.f0reach.jobs.yaml;

import me.f0reach.jobs.domain.job.AntiAutomationConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiAutomationParserTest {

    private final AntiAutomationParser parser = new AntiAutomationParser();

    private AntiAutomationConfig parseYaml(String yaml) throws IOException {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        return parser.parse(cfg.getConfigurationSection("anti_automation"), "anti_automation");
    }

    @Test
    void allKeysUnsetProducesAllNullFields() throws IOException {
        AntiAutomationConfig cfg = parseYaml("anti_automation: {}\n");
        assertNull(cfg.spawnerOriginKills());
        assertNull(cfg.unplantedCropHarvest());
        assertNull(cfg.recentlyPlacedBreak());
        assertNull(cfg.autoFedProcessing());
        assertNull(cfg.villagerRepeatTrade());
        assertNull(cfg.breedNonPlayerBreeder());
    }

    @Test
    void zeroValueEnablesEnumChecks() throws IOException {
        AntiAutomationConfig cfg = parseYaml("""
                anti_automation:
                  spawner_origin_kills: zero
                  unplanted_crop_harvest: zero
                  breed_non_player_breeder: zero
                """);
        assertEquals(AntiAutomationConfig.SpawnerOriginKills.ZERO, cfg.spawnerOriginKills());
        assertEquals(AntiAutomationConfig.UnplantedCropHarvest.ZERO, cfg.unplantedCropHarvest());
        assertEquals(AntiAutomationConfig.BreedNonPlayerBreeder.ZERO, cfg.breedNonPlayerBreeder());
    }

    @Test
    void offValueExplicitlyDisablesEnumChecks() throws IOException {
        // SnakeYAML の YAML 1.1 で "off" は Boolean.FALSE として読まれ得るが、
        // parser 側で Boolean -> "off" 文字列に降ろして扱う必要がある。
        AntiAutomationConfig cfg = parseYaml("""
                anti_automation:
                  spawner_origin_kills: "off"
                  unplanted_crop_harvest: "off"
                  breed_non_player_breeder: "off"
                """);
        assertEquals(AntiAutomationConfig.SpawnerOriginKills.DISABLED, cfg.spawnerOriginKills());
        assertEquals(AntiAutomationConfig.UnplantedCropHarvest.DISABLED, cfg.unplantedCropHarvest());
        assertEquals(AntiAutomationConfig.BreedNonPlayerBreeder.DISABLED, cfg.breedNonPlayerBreeder());
    }

    @Test
    void offScalarDisablesSectionCheck() throws IOException {
        AntiAutomationConfig cfg = parseYaml("""
                anti_automation:
                  recently_placed_break: "off"
                  auto_fed_processing: "off"
                  villager_repeat_trade: "off"
                """);
        assertFalse(cfg.recentlyPlacedBreak().enabled());
        assertFalse(cfg.autoFedProcessing().enabled());
        assertFalse(cfg.villagerRepeatTrade().enabled());
    }

    @Test
    void enabledFalseWithinSectionDisables() throws IOException {
        AntiAutomationConfig cfg = parseYaml("""
                anti_automation:
                  recently_placed_break:
                    enabled: false
                """);
        assertFalse(cfg.recentlyPlacedBreak().enabled());
    }

    @Test
    void sectionWithValuesEnablesCheck() throws IOException {
        AntiAutomationConfig cfg = parseYaml("""
                anti_automation:
                  recently_placed_break:
                    window_sec: 7200
                  auto_fed_processing:
                    operator_ttl_sec: 45
                  villager_repeat_trade:
                    cooldown_sec: 30
                    scope: recipe
                """);
        assertTrue(cfg.recentlyPlacedBreak().enabled());
        assertEquals(7200, cfg.recentlyPlacedBreak().windowSec());
        assertTrue(cfg.autoFedProcessing().enabled());
        assertEquals(45, cfg.autoFedProcessing().operatorTtlSec());
        assertTrue(cfg.villagerRepeatTrade().enabled());
        assertEquals(30, cfg.villagerRepeatTrade().cooldownSec());
    }

    @Test
    void unknownEnumValueThrows() {
        assertThrows(YamlParseException.class, () -> parseYaml("""
                anti_automation:
                  spawner_origin_kills: half
                """));
    }

    @Test
    void unsupportedScopeThrows() {
        assertThrows(YamlParseException.class, () -> parseYaml("""
                anti_automation:
                  villager_repeat_trade:
                    cooldown_sec: 30
                    scope: villager
                """));
    }
}
