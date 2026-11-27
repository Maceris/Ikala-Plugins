package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests for packing swatch pixels and the preview host's naming. */
class SwatchTest {

    @Test
    void pixelsArePackedAsHalfFloats() {
        final ImageFile image = Swatch.image("node", new float[] {0.25f, 2, 0, 1}, 1, 1);
        assertEquals(ImageFile.Depth.HALF, image.depth());
        assertEquals(1, image.width());
        final float[] pixel = image.pixel(0, 0, new float[4]);
        assertEquals(0.25f, pixel[0]);
        assertEquals(2, pixel[1], "Linear light above 1 is kept");
        assertEquals(1, pixel[3]);
    }

    @Test
    void theSizeMustMatchThePixels() {
        assertThrows(IllegalArgumentException.class, () -> Swatch.image("x", new float[4], 2, 1));
        assertThrows(IllegalArgumentException.class, () -> Swatch.image("x", new float[0], 0, 0));
    }

    @Test
    void capturesAreNamedAfterTheFile() {
        assertEquals("brick", PreviewHost.stem("brick.png"));
        assertEquals("brick.normal", PreviewHost.stem("brick.normal.png"));
        assertEquals(".hidden", PreviewHost.stem(".hidden"));
    }
}
