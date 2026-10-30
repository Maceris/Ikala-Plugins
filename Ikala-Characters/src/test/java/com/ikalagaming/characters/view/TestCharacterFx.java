package com.ikalagaming.characters.view;

import com.ikalagaming.characters.data.Color;
import com.ikalagaming.characters.data.Id;
import com.ikalagaming.characters.definition.FxUse;
import com.ikalagaming.graphics.scene.ParticleEffect;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for how {@link CharacterFx} tunes effects.
 *
 * @author Ches Burks
 */
class TestCharacterFx {
    /** How close floats must be. */
    private static final float EPSILON = 1e-5f;

    /** An unlit glowing effect to tune. */
    private static final ParticleEffect SPARKS =
            ParticleEffect.builder()
                    .rate(100)
                    .burst(10)
                    .speed(2, 4)
                    .spawnRadius(0.1f)
                    .acceleration(new Vector3f(0, -10, 0))
                    .size(0.2f, 0.1f)
                    .colors(new Vector4f(1, 0.5f, 0.25f, 1), new Vector4f(1, 1, 1, 0))
                    .emissive(2)
                    .lit(false)
                    .build();

    /**
     * An effect use with tuning.
     *
     * @param color The palette channel, or null.
     * @param scale The scale.
     * @param rate The rate.
     * @param intensity The intensity.
     * @return The use.
     */
    private static FxUse use(String color, float scale, float rate, float intensity) {
        return new FxUse(
                Id.parse("ikala:fx/sparks"),
                "hand.L",
                null,
                null,
                color,
                scale,
                rate,
                intensity,
                FxUse.ALWAYS,
                null,
                false);
    }

    /** Scale, rate and intensity multiply the effect's own values. */
    @Test
    void testTune() {
        ParticleEffect tuned = CharacterFx.tune(SPARKS, use(null, 2, 0.5f, 3), null);
        Assertions.assertEquals(50, tuned.getRate(), EPSILON);
        Assertions.assertEquals(5, tuned.getBurst());
        Assertions.assertEquals(4, tuned.getSpeedMin(), EPSILON);
        Assertions.assertEquals(8, tuned.getSpeedMax(), EPSILON);
        Assertions.assertEquals(0.2f, tuned.getSpawnRadius(), EPSILON);
        Assertions.assertEquals(-20, tuned.getAcceleration().y(), EPSILON);
        Assertions.assertEquals(0.4f, tuned.getSizeStart(), EPSILON);
        Assertions.assertEquals(0.2f, tuned.getSizeEnd(), EPSILON);
        Assertions.assertEquals(6, tuned.getEmissive(), EPSILON);
        // Unlit, so its color is brighter too, but not more opaque
        Assertions.assertEquals(3, tuned.getColorStart().x(), EPSILON);
        Assertions.assertEquals(1, tuned.getColorStart().w(), EPSILON);
        Assertions.assertEquals(0, tuned.getColorEnd().w(), EPSILON);
    }

    /** A tint multiplies the colors in linear light. */
    @Test
    void testTint() {
        Vector3f red = CharacterFx.linear(Color.parse("#ff0000"));
        Assertions.assertEquals(new Vector3f(1, 0, 0), red);
        Vector3f gray = CharacterFx.linear(Color.parse("#bcbcbc"));
        Assertions.assertEquals(0.5f, gray.x, 0.01f);

        ParticleEffect tuned = CharacterFx.tune(SPARKS, use("accent", 1, 1, 1), red);
        Assertions.assertEquals(1, tuned.getColorStart().x(), EPSILON);
        Assertions.assertEquals(0, tuned.getColorStart().y(), EPSILON);
        Assertions.assertEquals(0, tuned.getColorEnd().z(), EPSILON);
        Assertions.assertEquals(SPARKS.getColorStart().w(), tuned.getColorStart().w(), EPSILON);
    }
}
