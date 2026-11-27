package com.ikalagaming.graphics.scene.lights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Light directions as angles around and above the horizon. */
class LightAnglesTest {
    private static final double EPSILON = 1e-4;

    @Test
    void anglesNameWhereTheLightComesFrom() {
        assertTrue(
                new Vector3f(0, 0, 1).equals(LightAngles.direction(0, 0), (float) EPSILON),
                "Around 0 comes from -z, traveling toward +z");
        assertTrue(
                new Vector3f(-1, 0, 0).equals(LightAngles.direction(90, 0), (float) EPSILON),
                "Around 90 comes from +x");
        assertTrue(
                new Vector3f(0, -1, 0).equals(LightAngles.direction(30, 90), (float) EPSILON),
                "Straight above shines down");
    }

    @Test
    void directionsGiveTheirAnglesBack() {
        for (double around = 0; around < 360; around += 45) {
            for (double incline = -80; incline <= 80; incline += 20) {
                final Vector3f direction = LightAngles.direction(around, incline);
                assertEquals(around, LightAngles.around(direction), EPSILON);
                assertEquals(incline, LightAngles.incline(direction), EPSILON);
            }
        }
        // Not normalized, as the X, Y and Z sliders leave it
        assertEquals(45, LightAngles.incline(new Vector3f(0, -2, 2)), EPSILON);
        assertEquals(0, LightAngles.around(new Vector3f(0, -1, 0)), "Straight down has no around");
        assertEquals(0, LightAngles.incline(new Vector3f()));
    }

    @Test
    void theDayRisesAtSixAndPeaksAtNoon() {
        final Vector3f light = LightAngles.direction(120, 10);
        assertEquals(0, LightAngles.incline(LightAngles.atHour(6, light)), EPSILON);
        assertEquals(
                LightAngles.NOON_INCLINE,
                LightAngles.incline(LightAngles.atHour(12, light)),
                EPSILON);
        assertEquals(0, LightAngles.incline(LightAngles.atHour(18, light)), EPSILON);
        assertEquals(
                -LightAngles.NOON_INCLINE,
                LightAngles.incline(LightAngles.atHour(0, light)),
                EPSILON);
        assertEquals(
                120, LightAngles.around(LightAngles.atHour(9, light)), EPSILON, "Stays around");
    }
}
