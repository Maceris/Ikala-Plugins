package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** How surfaces reflect ambient light. */
class AmbientMathTest {

    private static final float EPSILON = 1e-5f;

    private static final float[] STEPS = {0, 0.05f, 0.25f, 0.5f, 0.75f, 1};

    private static final float[] ANGLES = {0, 0.01f, 0.1f, 0.3f, 0.6f, 1};

    private static final float SPECULAR = 0.5f;

    /** Diffuse plus specular, per channel. */
    private static Vector3f total(
            Vector3f baseLinear, float metallic, float roughness, float nDotV) {
        Vector3f diffuse = new Vector3f();
        Vector3f specular = new Vector3f();
        AmbientMath.reflectance(
                baseLinear, metallic, roughness, SPECULAR, 0, nDotV, diffuse, specular);
        return diffuse.add(specular);
    }

    @Test
    void metalsHaveNoDiffuseAmbient() {
        Vector3f diffuse = new Vector3f();
        Vector3f specular = new Vector3f();
        AmbientMath.reflectance(
                new Vector3f(0.9f, 0.6f, 0.2f), 1, 0.4f, SPECULAR, 0, 0.7f, diffuse, specular);
        assertEquals(0, diffuse.length(), EPSILON);
        // Gold reflects gold
        assertTrue(specular.x > specular.y && specular.y > specular.z, specular.toString());
    }

    @Test
    void aWhiteSurfaceReflectsAllTheAmbientLight() {
        // The white furnace: nothing is lost at any roughness or angle. Part metallic surfaces are
        // a blend the model doesn't conserve energy for, so only the two real cases are checked.
        Vector3f white = new Vector3f(1);
        for (float metallic : new float[] {0, 1}) {
            for (float roughness : STEPS) {
                for (float nDotV : ANGLES) {
                    Vector3f reflected = total(white, metallic, roughness, nDotV);
                    final String at = metallic + " " + roughness + " " + nDotV;
                    assertEquals(1, reflected.x, EPSILON, at);
                    assertEquals(1, reflected.z, EPSILON, at);
                }
            }
        }
    }

    @Test
    void noSurfaceReflectsMoreThanArrives() {
        Vector3f[] colors = {
            new Vector3f(0),
            new Vector3f(0.04f),
            new Vector3f(0.5f, 0.2f, 0.1f),
            new Vector3f(0.95f)
        };
        for (Vector3f color : colors) {
            for (float metallic : STEPS) {
                for (float roughness : STEPS) {
                    for (float nDotV : ANGLES) {
                        Vector3f reflected = total(color, metallic, roughness, nDotV);
                        final String at = color + " " + metallic + " " + roughness + " " + nDotV;
                        assertTrue(reflected.maxComponent() <= 1 + EPSILON, at);
                        assertTrue(reflected.minComponent() >= 0, at);
                    }
                }
            }
        }
    }

    @Test
    void roughMetalsKeepTheirBrightness() {
        // Single scattering alone loses up to about half the light on rough metals
        Vector2f scaleBias = AmbientMath.environmentBrdf(1, 0.5f, new Vector2f());
        final float singleOnly = scaleBias.x + scaleBias.y;
        assertTrue(singleOnly < 0.6f, "" + singleOnly);
        assertEquals(1, total(new Vector3f(1), 1, 1, 0.5f).y, EPSILON);
    }

    @Test
    void theFitMatchesKnownValues() {
        // Smooth and head on: everything in the scale, almost nothing in the bias
        Vector2f smooth = AmbientMath.environmentBrdf(0, 1, new Vector2f());
        assertEquals(0.994f, smooth.x, 1e-3f);
        assertEquals(0.006f, smooth.y, 1e-3f);
        // Smooth and grazing: everything reflects, whatever the color
        Vector2f grazing = AmbientMath.environmentBrdf(0, 0, new Vector2f());
        assertEquals(1, grazing.x + grazing.y, 1e-3f);
    }

    @Test
    void dielectricsReflectALittleAndMetalsTheirColor() {
        Vector3f base = new Vector3f(0.2f, 0.5f, 0.8f);
        Vector3f plastic = AmbientMath.specularColor(base, 0, 0.5f, 0, new Vector3f());
        assertEquals(0.04f, plastic.x, EPSILON);
        assertEquals(0.04f, plastic.z, EPSILON);
        Vector3f metal = AmbientMath.specularColor(base, 1, 0.5f, 0, new Vector3f());
        assertTrue(metal.equals(base, EPSILON));
    }
}
