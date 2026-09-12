package com.ikalagaming.graphics.frontend.gui.backend.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ikalagaming.graphics.backend.vulkan.SharedBuffer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class SharedBufferTest {

    private static Stream<Arguments> alignSingleProvider() {
        return Stream.of(
                Arguments.of(-10, 0),
                Arguments.of(0, 0),
                Arguments.of(1, 16),
                Arguments.of(15, 16),
                Arguments.of(16, 16),
                Arguments.of(17, 32));
    }

    private static Stream<Arguments> alignDoubleProvider() {
        return Stream.of(
                Arguments.of(-10, 4, 0),
                Arguments.of(0, 4, 0),
                Arguments.of(44, -10, 0),
                Arguments.of(44, 0, 0),
                Arguments.of(0, 0, 0),
                Arguments.of(1, 16, 16),
                Arguments.of(5, 7, 16),
                Arguments.of(18, 7, 32),
                Arguments.of(29, 30, 32));
    }

    private static Stream<Arguments> alignWithoutPaddingProvider() {
        return Stream.of(
                Arguments.of(-10, 4, 0),
                Arguments.of(0, 4, 0),
                Arguments.of(44, -10, 0),
                Arguments.of(44, 0, 0),
                Arguments.of(0, 0, 0),
                Arguments.of(1, 16, 16),
                Arguments.of(56, 25, 75),
                Arguments.of(5, 7, 7),
                Arguments.of(29, 30, 30));
    }

    @ParameterizedTest
    @MethodSource("alignSingleProvider")
    void testAlignBytes(final long bytes, final long expected) {
        long aligned = SharedBuffer.align(bytes);
        assertEquals(expected, aligned);
    }

    @ParameterizedTest
    @MethodSource("alignDoubleProvider")
    void testAlignBytesAndAlignment(final long bytes, final long alignment, final long expected) {
        long aligned = SharedBuffer.align(bytes, alignment);
        assertEquals(expected, aligned);
    }

    @ParameterizedTest
    @MethodSource("alignWithoutPaddingProvider")
    void testAlignWithoutPadding(final long bytes, final long alignment, final long expected) {
        long aligned = SharedBuffer.alignWithoutPadding(bytes, alignment);
        assertEquals(expected, aligned);
    }
}
