package com.ikalagaming.graphics.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

/** How a gate benchmark run reads its options. */
class GateOptionsTest {

    /** A startup property the tests set, cleared after each. */
    private static final String PROPERTY = GateBenchmark.PROPERTY + ".optionsTest";

    /** Clear the startup property. */
    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    /** What the run sets comes before the startup property, which comes before the default. */
    @Test
    void testPrecedence() {
        final GateOptions options = new GateOptions();
        assertNull(options.get("optionsTest"));
        assertEquals("fallback", options.get("optionsTest", "fallback"));
        System.setProperty(PROPERTY, "property");
        assertEquals("property", options.get("optionsTest", "fallback"));
        options.putAll(Map.of("optionsTest", "set"));
        assertEquals("set", options.get("optionsTest"));
    }

    /** Flags are true only when they say so. */
    @Test
    void testFlag() {
        final GateOptions options = new GateOptions();
        assertFalse(options.flag("optionsTest"));
        options.putAll(Map.of("optionsTest", "TRUE"));
        assertTrue(options.flag("optionsTest"));
        options.putAll(Map.of("optionsTest", "yes"));
        assertFalse(options.flag("optionsTest"));
    }

    /** Points are three values; anything else is no point. */
    @Test
    void testPoint() {
        final GateOptions options = new GateOptions();
        assertNull(options.point("optionsTest"));
        options.putAll(Map.of("optionsTest", " 1.5, -2 ,3"));
        assertEquals(new Vector3d(1.5, -2, 3), options.point("optionsTest"));
        options.putAll(Map.of("optionsTest", "1,2"));
        assertNull(options.point("optionsTest"));
    }

    /** The turn takes its default time unless the option says, and at least a second. */
    @Test
    void testMeasureSeconds() {
        final GateOptions options = new GateOptions();
        options.putAll(Map.of("seconds", "20"));
        assertEquals(20, options.measureSeconds());
        options.putAll(Map.of("seconds", "0.25"));
        assertEquals(1, options.measureSeconds());
    }
}
