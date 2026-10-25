package com.ikalagaming.characters.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ColorCode}.
 *
 * @author Ches Burks
 */
class TestColorCode {
    /** Every code survives decoding and encoding again, so snapping a snapped color is stable. */
    @Test
    void testRoundTrip() {
        float[] rgb = new float[3];
        for (int code = 0; code < 1 << 16; ++code) {
            ColorCode.decode(code, rgb);
            int again = ColorCode.encode(rgb[0], rgb[1], rgb[2]);
            if (ColorCode.saturation(code) == 0 || ColorCode.value(code) == 0) {
                // Grays and black have no hue, and black no saturation either
                Assertions.assertEquals(ColorCode.value(code), ColorCode.value(again));
                continue;
            }
            Assertions.assertEquals(code, again, "code " + Integer.toHexString(code));
        }
    }

    /** Known colors land on the expected steps, and codes fit in 16 bits. */
    @Test
    void testKnownColors() {
        Assertions.assertEquals(ColorCode.of(0, 0, 0), ColorCode.encode(0, 0, 0));
        Assertions.assertEquals(ColorCode.of(0, 0, 63), ColorCode.encode(1, 1, 1));
        Assertions.assertEquals(ColorCode.of(0, 15, 63), ColorCode.encode(1, 0, 0));
        Assertions.assertEquals(ColorCode.of(21, 15, 63), ColorCode.encode(0, 1, 0));
        Assertions.assertEquals(ColorCode.of(43, 15, 63), ColorCode.encode(0, 0, 1));
        Assertions.assertEquals(new Color(255, 0, 0), ColorCode.toColor(ColorCode.of(0, 15, 63)));
        Assertions.assertEquals(0xFFFF, ColorCode.of(63, 15, 63));

        // A skin tone snaps to a color within a step of itself
        Color skin = Color.parse("#c58c62");
        Color snapped = ColorCode.toColor(ColorCode.encode(skin));
        Assertions.assertTrue(Math.abs(skin.red() - snapped.red()) <= 8, snapped.toString());
        Assertions.assertTrue(Math.abs(skin.green() - snapped.green()) <= 8, snapped.toString());
        Assertions.assertTrue(Math.abs(skin.blue() - snapped.blue()) <= 8, snapped.toString());
    }

    /** Steps out of range are refused. */
    @Test
    void testRange() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ColorCode.of(64, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ColorCode.of(0, 16, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ColorCode.of(0, 0, -1));
    }
}
