package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.WaterBody;

import org.junit.jupiter.api.Test;

/** The optics of water, and the light a column of it scatters back. */
class WaterMathTest {

    /** Red, green and blue. */
    private static final int RED = 0;

    private static final int GREEN = 1;

    private static final int BLUE = 2;

    /** Pure water absorbs red most and scatters blue most, the same every way. */
    @Test
    void testPureWater() {
        final WaterMath.Optics optics = WaterMath.optics(new WaterBody(0, 0, 0));
        for (int channel = 0; channel < WaterMath.CHANNELS; ++channel) {
            assertEquals(
                    WaterMath.PURE_WATER_ABSORPTION[channel], optics.absorption()[channel], 1e-12);
            assertEquals(optics.scattering()[channel] / 2, optics.backscattering()[channel], 1e-12);
        }
        assertTrue(optics.absorption()[RED] > optics.absorption()[GREEN]);
        assertTrue(optics.absorption()[GREEN] > optics.absorption()[BLUE]);
        assertTrue(optics.scattering()[BLUE] > optics.scattering()[RED]);
        // Morel's 0.00288 at 500 nm, falling as l^-4.32
        assertEquals(0.002_88 * Math.pow(450.0 / 500, -4.32), optics.scattering()[BLUE], 1e-12);
    }

    /** Phytoplankton absorb blue more than green, so green water lets green through best. */
    @Test
    void testChlorophyllTurnsWaterGreen() {
        final WaterMath.Optics clear = WaterMath.optics(new WaterBody(0, 0, 0));
        final WaterMath.Optics green = WaterMath.optics(WaterBody.GREEN);
        assertTrue(
                green.absorption()[BLUE] - clear.absorption()[BLUE]
                        > 2 * (green.absorption()[GREEN] - clear.absorption()[GREEN]));
        assertTrue(green.extinction(GREEN) < green.extinction(BLUE));
        assertTrue(green.extinction(GREEN) < green.extinction(RED));
        assertTrue(green.scattering()[GREEN] > 10 * clear.scattering()[GREEN]);
        assertTrue(
                green.backscattering()[GREEN] < 0.05 * green.scattering()[GREEN],
                "particles scatter forward");
        // 0.06 a*(l) C^0.65, plus CDOM a fifth of it at 440 nm, falling with wavelength
        final double chlorophyll = WaterBody.GREEN.chlorophyll();
        final double phytoplankton =
                0.06 * WaterMath.CHLOROPHYLL_SPECIFIC[BLUE] * Math.pow(chlorophyll, 0.65);
        final double cdom = 0.2 * 0.06 * Math.pow(chlorophyll, 0.65) * Math.exp(-0.014 * 10);
        assertEquals(
                WaterMath.PURE_WATER_ABSORPTION[BLUE] + phytoplankton + cdom,
                green.absorption()[BLUE],
                1e-6);
    }

    /** Dissolved organic matter absorbs blue far more than red. */
    @Test
    void testCdomAbsorbsBlue() {
        final WaterMath.Optics clear = WaterMath.optics(new WaterBody(0, 0, 0));
        final WaterMath.Optics brown = WaterMath.optics(new WaterBody(0, 2, 0));
        final double blue = brown.absorption()[BLUE] - clear.absorption()[BLUE];
        final double red = brown.absorption()[RED] - clear.absorption()[RED];
        assertEquals(2 * Math.exp(-0.014 * 10), blue, 1e-9);
        assertTrue(blue > 10 * red);
        assertEquals(clear.scattering()[GREEN], brown.scattering()[GREEN], 1e-12);
    }

    /** Sediment scatters every color, forward, and absorbs blue a little more. */
    @Test
    void testSedimentScatters() {
        final WaterMath.Optics muddy = WaterMath.optics(new WaterBody(0, 0, 40));
        assertEquals(
                0.5 * 40 + WaterMath.optics(new WaterBody(0, 0, 0)).scattering()[GREEN],
                muddy.scattering()[GREEN],
                1e-9);
        assertEquals(0.0183, muddy.backscattering()[RED] / muddy.scattering()[RED], 1e-3);
        assertTrue(muddy.scattering()[GREEN] > muddy.absorption()[GREEN]);
        assertTrue(muddy.absorption()[BLUE] > muddy.absorption()[GREEN]);
    }

    /** Case 1 water's dissolved organic matter follows its chlorophyll. */
    @Test
    void testCaseOneBody() {
        final WaterBody body = WaterBody.ofChlorophyll(2);
        assertEquals(0.2 * 0.06 * Math.pow(2, 0.65), body.cdom(), 1e-6);
        assertEquals(0, body.sediment());
        assertEquals(0, WaterBody.ofChlorophyll(0).cdom());
    }

    /** About 2% is reflected looking straight down, all of it at grazing angles. */
    @Test
    void testFresnel() {
        assertEquals(0.0204, WaterMath.fresnel(1), 1e-4);
        assertEquals(1, WaterMath.fresnel(0), 1e-12);
        assertTrue(WaterMath.fresnel(0.2) > WaterMath.fresnel(0.5));
    }

    /** Light bends toward the normal going in, no further than the critical angle. */
    @Test
    void testRefraction() {
        assertEquals(1, WaterMath.refractedCosine(1), 1e-12);
        final double critical = Math.sqrt(1 - 1 / (1.333 * 1.333));
        assertEquals(critical, WaterMath.refractedCosine(0), 1e-12);
        assertTrue(WaterMath.refractedCosine(0.5) > 0.5);
    }

    /**
     * Deep water sends back 0.33 b_b / (a + b_b) of the light coming down; a column reaches that as
     * the light's way down and back up grows, and sends back nothing with no depth.
     */
    @Test
    void testReflectance() {
        assertEquals(0.33 * 0.02 / 0.1, WaterMath.deepReflectance(0.08, 0.02), 1e-12);
        assertEquals(0, WaterMath.deepReflectance(0, 0));
        assertEquals(0, WaterMath.columnReflectance(0.08, 0.02, 0, 0), 1e-12);
        final double half =
                WaterMath.columnReflectance(
                        0.08, 0.02, Math.log(2) / 0.1 / 2, Math.log(2) / 0.1 / 2);
        assertEquals(WaterMath.deepReflectance(0.08, 0.02) / 2, half, 1e-12);
        assertEquals(
                WaterMath.deepReflectance(0.08, 0.02),
                WaterMath.columnReflectance(0.08, 0.02, 500, 500),
                1e-12);
    }

    /**
     * Muddy water is bright and tan: it sends back far more red than clear water does, and more red
     * than blue; clear ocean sends back blue most.
     */
    @Test
    void testWaterColors() {
        final WaterMath.Optics ocean = WaterMath.optics(WaterBody.OCEAN);
        final WaterMath.Optics muddy = WaterMath.optics(WaterBody.MUDDY);
        final double[] oceanColor = new double[WaterMath.CHANNELS];
        final double[] muddyColor = new double[WaterMath.CHANNELS];
        for (int channel = 0; channel < WaterMath.CHANNELS; ++channel) {
            oceanColor[channel] =
                    WaterMath.deepReflectance(
                            ocean.absorption()[channel], ocean.backscattering()[channel]);
            muddyColor[channel] =
                    WaterMath.deepReflectance(
                            muddy.absorption()[channel], muddy.backscattering()[channel]);
        }
        assertTrue(oceanColor[BLUE] > 5 * oceanColor[RED], "the open ocean is blue");
        assertTrue(muddyColor[RED] > muddyColor[BLUE], "muddy water is tan");
        assertTrue(muddyColor[RED] > 0.05, "and bright: " + muddyColor[RED]);
    }
}
