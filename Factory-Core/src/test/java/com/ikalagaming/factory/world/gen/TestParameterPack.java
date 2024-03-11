package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for the parameter packs.
 *
 * @author Ches Burks
 */
@ExtendWith(MockitoExtension.class)
class TestParameterPack {

    @Mock private NoiseGenerator generator;

    /** Check that parameter packs will generate correctly. */
    @Test
    void testParameterPackGeneration() {

        var expectedValue = 0.5d;
        var seed = 123L;
        var x = 1;
        var y = 1;

        given(generator.getNoise(anyLong(), anyInt(), anyInt(), anyDouble(), anyInt()))
                .willReturn(expectedValue);

        var result = ParameterPack.generateParameters(generator, seed, x, y);

        assertAll(
                () -> assertEquals(expectedValue, result.temperature()),
                () -> assertEquals(expectedValue, result.height()),
                () -> assertEquals(expectedValue, result.erosion()),
                () -> assertEquals(expectedValue, result.precipitation()),
                () -> assertEquals(expectedValue, result.weirdness()));
    }
}
