package com.ikalagaming.factory.world.gen;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the parameter packs.
 *
 * @author Ches Burks
 */
class TestParameterPack {

    @BeforeEach
    void setup() {}

    /** Check that width is calculated correctly. */
    @Test
    void test() {
        final float epsilon = 0.0001f;
        final float width = new ParameterRange(0.4f, 0.5f).getWidth();
        final float expectedWidth = 0.1f;

        Assertions.assertTrue(Math.abs(expectedWidth - width) < epsilon);
    }
}
