package me.f0reach.jobs.domain.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiAutomationConfigMergeTest {

    @Test
    void overrideNullFieldsInheritFromDefaults() {
        AntiAutomationConfig defaults = new AntiAutomationConfig(
                AntiAutomationConfig.SpawnerOriginKills.ZERO,
                AntiAutomationConfig.UnplantedCropHarvest.ZERO,
                new AntiAutomationConfig.RecentlyPlacedBreak(3600, true),
                new AntiAutomationConfig.AutoFedProcessing(60, true),
                new AntiAutomationConfig.VillagerRepeatTrade(60, true),
                AntiAutomationConfig.BreedNonPlayerBreeder.ZERO
        );
        AntiAutomationConfig override = AntiAutomationConfig.empty();

        AntiAutomationConfig merged = AntiAutomationConfig.merge(defaults, override);

        assertEquals(AntiAutomationConfig.SpawnerOriginKills.ZERO, merged.spawnerOriginKills());
        assertEquals(AntiAutomationConfig.UnplantedCropHarvest.ZERO, merged.unplantedCropHarvest());
        assertTrue(merged.recentlyPlacedBreak().enabled());
        assertEquals(3600, merged.recentlyPlacedBreak().windowSec());
        assertTrue(merged.autoFedProcessing().enabled());
        assertTrue(merged.villagerRepeatTrade().enabled());
        assertEquals(AntiAutomationConfig.BreedNonPlayerBreeder.ZERO, merged.breedNonPlayerBreeder());
    }

    @Test
    void overrideExplicitDisabledWinsOverDefaultEnabled() {
        AntiAutomationConfig defaults = new AntiAutomationConfig(
                AntiAutomationConfig.SpawnerOriginKills.ZERO,
                null,
                new AntiAutomationConfig.RecentlyPlacedBreak(3600, true),
                null, null, null
        );
        AntiAutomationConfig override = new AntiAutomationConfig(
                AntiAutomationConfig.SpawnerOriginKills.DISABLED,
                null,
                AntiAutomationConfig.RecentlyPlacedBreak.disabled(),
                null, null, null
        );

        AntiAutomationConfig merged = AntiAutomationConfig.merge(defaults, override);

        assertEquals(AntiAutomationConfig.SpawnerOriginKills.DISABLED, merged.spawnerOriginKills());
        assertFalse(merged.recentlyPlacedBreak().enabled());
    }

    @Test
    void overrideValueReplacesDefaultParameter() {
        AntiAutomationConfig defaults = new AntiAutomationConfig(
                null, null,
                new AntiAutomationConfig.RecentlyPlacedBreak(3600, true),
                null, null, null
        );
        AntiAutomationConfig override = new AntiAutomationConfig(
                null, null,
                new AntiAutomationConfig.RecentlyPlacedBreak(120, true),
                null, null, null
        );

        AntiAutomationConfig merged = AntiAutomationConfig.merge(defaults, override);

        assertEquals(120, merged.recentlyPlacedBreak().windowSec());
    }

    @Test
    void nullOverrideReturnsDefaults() {
        AntiAutomationConfig defaults = new AntiAutomationConfig(
                AntiAutomationConfig.SpawnerOriginKills.ZERO,
                null, null, null, null, null
        );

        AntiAutomationConfig merged = AntiAutomationConfig.merge(defaults, null);

        assertEquals(AntiAutomationConfig.SpawnerOriginKills.ZERO, merged.spawnerOriginKills());
    }

    @Test
    void nullDefaultsBehavesLikeEmpty() {
        AntiAutomationConfig override = new AntiAutomationConfig(
                AntiAutomationConfig.SpawnerOriginKills.ZERO,
                null, null, null, null, null
        );

        AntiAutomationConfig merged = AntiAutomationConfig.merge(null, override);

        assertEquals(AntiAutomationConfig.SpawnerOriginKills.ZERO, merged.spawnerOriginKills());
        assertNull(merged.unplantedCropHarvest());
    }
}
