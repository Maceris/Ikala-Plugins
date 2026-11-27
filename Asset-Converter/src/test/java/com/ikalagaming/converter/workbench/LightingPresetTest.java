package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.LightUnits;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reading lighting presets, and what light they make. */
class LightingPresetTest {
    private static final String CAVE =
            """
            name: Torch cave
            order: 3
            sky: none
            lights:
              - at: [0.8, 1.2, 0.6]
                color: [1.0, 0.62, 0.3]
                candela: 80
                range: 8
            exposure: 4
            """;

    @Test
    void readsEverySetting() {
        LightingPreset noon =
                LightingPreset.parse(
                        """
                        name: Noon
                        sun:
                          around: 90
                          up: 0
                          color: [1, 0.9, 0.8]
                          lux: 100000
                        sky: physical
                        glow: 0.002
                        stars: true
                        exposure: 15
                        """);
        assertEquals("Noon", noon.name());
        assertEquals(LightingPreset.Sky.PHYSICAL, noon.sky());
        assertEquals(100_000, noon.sun().lux());
        assertEquals(new Vector3f(1, 0.9f, 0.8f), noon.sun().color());
        assertTrue(noon.stars());
        assertEquals(15, noon.exposure());
        Vector3f direction = noon.sun().direction();
        assertEquals(1, direction.x, 1e-6, "Turned to +x, on the horizon");
        assertEquals(0, direction.y, 1e-6);
    }

    @Test
    void aCaveHasOnlyItsLight() {
        LightingPreset cave = LightingPreset.parse(CAVE);
        assertNull(cave.sun());
        assertEquals(LightingPreset.Sky.NONE, cave.sky());
        assertEquals(1, cave.lights().size());
        assertEquals(80, cave.lights().getFirst().candela());
        assertEquals(8, cave.lights().getFirst().range());
        assertEquals(80, cave.illuminance(), "A light counts by what it gives a meter away");
        assertTrue(
                LightUnits.preExposure(cave.illuminance()) > LightUnits.REFERENCE_PRE_EXPOSURE,
                "A dim scene keeps more precision");
    }

    @Test
    void mistakesAreNamed() {
        IllegalArgumentException noName =
                assertThrows(
                        IllegalArgumentException.class, () -> LightingPreset.parse("sky: none"));
        assertTrue(noName.getMessage().contains("name"));
        IllegalArgumentException badSky =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> LightingPreset.parse("name: x\nsky: purple"));
        assertTrue(badSky.getMessage().contains("sky"));
        IllegalArgumentException badColor =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> LightingPreset.parse("name: x\nsun:\n  color: [1, 2]"));
        assertTrue(badColor.getMessage().contains("color"));
    }

    @Test
    void aFolderIsReadInOrderSkippingBadFiles(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("cave.lighting.yml"), CAVE);
        Files.writeString(folder.resolve("first.lighting.yml"), "name: First\norder: -1");
        Files.writeString(folder.resolve("broken.lighting.yml"), "sky: none");
        Files.writeString(folder.resolve("other.yml"), "name: Not a preset");
        List<LightingPreset> presets = LightingPreset.loadAll(folder);
        assertEquals(
                List.of("First", "Torch cave"),
                presets.stream().map(LightingPreset::name).toList());
    }
}
