package com.ikalagaming.factory.world.gen;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Tests for the parameter range.
 *
 * @author Ches Burks
 */
class TestParameterRange {

    private static Stream<Arguments> constructorArgumentProvider() {
        return Stream.of(
                Arguments.of(0.0f, 1.0f), Arguments.of(0.10f, 0.101f), Arguments.of(0.1f, 0.112f));
    }

    private static Stream<Arguments> constructorIllegalArgumentProvider() {
        return Stream.of(
                Arguments.of(-1.0f, 1.0f),
                Arguments.of(0.0f, 1.1f),
                Arguments.of(0.5f, 0.4f),
                Arguments.of(Float.NaN, 1.0f),
                Arguments.of(0.0f, Float.NaN));
    }

    private static Stream<Arguments> containsProvider() {
        return Stream.of(
                Arguments.of(0.1f, 0.3f, 0.2f, true),
                Arguments.of(0.1f, 0.3f, 0.1f, true),
                Arguments.of(0.1f, 0.3f, 0.3f, true),
                Arguments.of(0.1f, 0.3f, 0.0999f, false),
                Arguments.of(0.1f, 0.3f, 0.3001f, false));
    }

    /**
     * Test the contains method accurately checks values.
     *
     * @param min The min value of the range.
     * @param max The max value of the range.
     * @param value The value to check.
     * @param expectedResult Whether the range should contain that value.
     */
    @ParameterizedTest
    @MethodSource("containsProvider")
    void testContains(
            final float min, final float max, final float value, final boolean expectedResult) {

        var range = new ParameterRange(min, max);

        Assertions.assertEquals(expectedResult, range.contains(value));
    }

    /**
     * Test the creation of invalid parameter ranges.
     *
     * @param min The min value.
     * @param max The max value.
     */
    @ParameterizedTest
    @MethodSource("constructorIllegalArgumentProvider")
    void testIllegalConstructor(final float min, final float max) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ParameterRange(min, max));
    }

    /** Check that midpoint is calculated correctly. */
    @Test
    void testMidpoint() {
        final float epsilon = 0.0001f;
        final float midpoint = new ParameterRange(0.3f, 0.5f).getMidpoint();
        final float expectedMidpoint = 0.4f;

        Assertions.assertTrue(Math.abs(expectedMidpoint - midpoint) < epsilon);
    }

    /**
     * Test the creation normal parameter ranges.
     *
     * @param min The min value.
     * @param max The max value.
     */
    @ParameterizedTest
    @MethodSource("constructorArgumentProvider")
    void testNormalConstructor(final float min, final float max) {
        Assertions.assertDoesNotThrow(() -> new ParameterRange(min, max));
    }

    /** Check that width is calculated correctly. */
    @Test
    void testWidth() {
        final float epsilon = 0.0001f;
        final float width = new ParameterRange(0.4f, 0.5f).getWidth();
        final float expectedWidth = 0.1f;

        Assertions.assertTrue(Math.abs(expectedWidth - width) < epsilon);
    }
}
