package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for physical light units, pre-exposure and exposure values. */
class LightUnitsTest {

    /** Full daylight comes to the old sun of 3 on the GPU at the reference pre-exposure. */
    @Test
    void testReferenceScale() {
        assertEquals(
                LightUnits.REFERENCE_SUN,
                LightUnits.DAYLIGHT_LUX * LightUnits.REFERENCE_PRE_EXPOSURE,
                1e-5);
        assertEquals(LightUnits.DAYLIGHT_LUX, LightUnits.physical(LightUnits.REFERENCE_SUN), 1);
        assertEquals(0.25f, LightUnits.reference(LightUnits.physical(0.25f)), 1e-6);
    }

    /** The default exposure shows the reference scale as an exposure of 1 did, about EV 14.8. */
    @Test
    void testDefaultExposure() {
        assertEquals(14.76, LightUnits.DEFAULT_EV100, 0.01);
        final Scene scene = new Scene(16, 16);
        assertEquals(1, scene.displayExposure(), 1e-4);
        // Pre-exposing more changes nothing shown
        scene.setPreExposure(LightUnits.REFERENCE_PRE_EXPOSURE * 1000);
        assertEquals(1e-3, scene.displayExposure(), 1e-7);
        // One EV more is half as bright
        scene.setPreExposure(LightUnits.REFERENCE_PRE_EXPOSURE);
        scene.setExposureValue(LightUnits.DEFAULT_EV100 + 1);
        assertEquals(0.5, scene.displayExposure(), 1e-4);
        assertEquals(1, LightUnits.ev100(LightUnits.exposure(1)), 1e-5);
    }

    /** While adapting, only the compensation counts, in stops. */
    @Test
    void testCompensation() {
        final Scene scene = new Scene(16, 16);
        scene.setAutoExposure(true);
        assertEquals(1, scene.displayExposure(), 1e-6);
        scene.setExposureCompensation(-2);
        assertEquals(0.25, scene.displayExposure(), 1e-6);
    }
}
