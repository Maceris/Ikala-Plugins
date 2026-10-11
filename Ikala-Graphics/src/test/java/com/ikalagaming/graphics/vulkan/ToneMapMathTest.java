package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The color math between linear light and the image shown. */
class ToneMapMathTest {

    @Test
    void decodeAndEncodeAreInverses() {
        for (int i = 0; i <= 255; ++i) {
            float srgb = i / 255f;
            assertEquals(srgb, ToneMapMath.encode(ToneMapMath.decode(srgb)), 1e-5, "At " + i);
        }
        // Mid grey in sRGB is about a fifth of the light
        assertEquals(0.214, ToneMapMath.decode(0.5f), 1e-3);
        assertEquals(0, ToneMapMath.decode(0));
        assertEquals(1, ToneMapMath.decode(1), 1e-6);
    }

    @Test
    void blackStaysBlackAndBrighterIsNeverDarker() {
        assertEquals(new Vector3f(), ToneMapMath.pbrNeutral(new Vector3f()));
        float previous = -1;
        for (float light = 0; light < 20; light += 0.01f) {
            float shown = ToneMapMath.pbrNeutral(new Vector3f(light)).x;
            assertTrue(shown >= previous, "At " + light);
            assertTrue(shown <= 1, "At " + light);
            previous = shown;
        }
    }

    @Test
    void colorsBelowTheKneeKeepTheirHue() {
        // A green leaf, well below where highlights start compressing
        Vector3f leaf = new Vector3f(0.05f, 0.4f, 0.03f);
        Vector3f shown = ToneMapMath.pbrNeutral(new Vector3f(leaf));
        // Shifted down by the same small offset in every channel, so the differences stay
        assertEquals(leaf.y - leaf.x, shown.y - shown.x, 1e-5);
        assertEquals(leaf.y - leaf.z, shown.y - shown.z, 1e-5);
        assertTrue(shown.y > shown.x && shown.y > shown.z, "Still green");
    }
}
