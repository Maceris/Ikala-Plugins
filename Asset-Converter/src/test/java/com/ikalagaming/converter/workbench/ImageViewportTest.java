package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the image view's pan and zoom math, and the viewer's helpers. */
class ImageViewportTest {

    @Test
    void testFitCenters() {
        ImageViewport viewport = new ImageViewport();
        // A wide image in a square view fits its width
        viewport.fit(200, 100, 400, 400);
        assertEquals(2, viewport.getScale(), 0.0001f);
        assertEquals(0, viewport.getOffsetX(), 0.0001f);
        assertEquals(100, viewport.getOffsetY(), 0.0001f);
    }

    @Test
    void testActualSizeCenters() {
        ImageViewport viewport = new ImageViewport();
        viewport.actualSize(100, 50, 301, 300);
        assertEquals(1, viewport.getScale(), 0.0001f);
        // Whole pixels, rounded down
        assertEquals(100, viewport.getOffsetX(), 0.0001f);
        assertEquals(125, viewport.getOffsetY(), 0.0001f);
    }

    @Test
    void testZoomKeepsPointUnderCursor() {
        ImageViewport viewport = new ImageViewport();
        viewport.actualSize(100, 100, 100, 100);
        // Pixel centers, so rounding can't tip the floor either way
        final int before = viewport.imageX(37.5f);
        viewport.zoom(3, 37.5f, 61.5f);
        assertEquals((float) Math.pow(ImageViewport.ZOOM_STEP, 3), viewport.getScale(), 0.0001f);
        assertEquals(before, viewport.imageX(37.5f));
        assertEquals(61, viewport.imageY(61.5f));
    }

    @Test
    void testZoomIsClamped() {
        ImageViewport viewport = new ImageViewport();
        viewport.zoom(1000, 0, 0);
        assertEquals(ImageViewport.MAX_SCALE, viewport.getScale(), 0.0001f);
        viewport.zoom(-1000, 0, 0);
        assertEquals(ImageViewport.MIN_SCALE, viewport.getScale(), 0.0001f);
    }

    @Test
    void testPanAndHover() {
        ImageViewport viewport = new ImageViewport();
        viewport.actualSize(10, 10, 10, 10);
        viewport.zoom(0, 0, 0);
        viewport.pan(5, -5);
        assertEquals(0, viewport.imageX(5));
        assertEquals(-1, viewport.imageX(4.5f));
        assertEquals(5, viewport.imageY(0));
    }

    @Test
    void testMipLevels() {
        assertEquals(1, ImageViewer.mipLevels(1, 1));
        assertEquals(11, ImageViewer.mipLevels(1024, 1024));
        assertEquals(11, ImageViewer.mipLevels(1024, 3));
        assertEquals(11, ImageViewer.mipLevels(1025, 1));
        assertEquals(12, ImageViewer.mipLevels(2048, 1));
    }

    @Test
    void testNormalMapNames() {
        assertTrue(ImageViewer.looksLikeNormalMap("Antenna_Metal_Normal.dds"));
        assertTrue(ImageViewer.looksLikeNormalMap("rock_nrm.tga"));
        assertTrue(ImageViewer.looksLikeNormalMap("brick_nor.png"));
        assertFalse(ImageViewer.looksLikeNormalMap("brick_BaseColor.png"));
    }

    @Test
    void testPixelText() {
        float[] value = {1, 0.5f, 0, 1};
        assertEquals(
                "R 255  G 128  B 0  A 255", ImageViewer.pixelText(ImageFile.Depth.UNORM8, value));
        assertEquals(
                "R 1.0000  G 0.5000  B 0.0000  A 1.0000",
                ImageViewer.pixelText(ImageFile.Depth.HALF, value));
    }

    @Test
    void testSizeText() {
        assertEquals("512 bytes", ImageViewer.sizeText(512));
        assertEquals("1.5 KB", ImageViewer.sizeText(1536));
        assertEquals("2.0 MB", ImageViewer.sizeText(2 * 1024 * 1024));
    }
}
