package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ikalagaming.graphics.gui.data.TextureView;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Tests for the texture viewer's measure and capture, which work on the CPU. */
class TextureViewerTest {

    /** Where test images are written. */
    @TempDir Path folder;

    /** Settings that show the stored values as they are. */
    private static final TextureViewer.Settings RAW =
            new TextureViewer.Settings(TextureView.Channel.RGBA, 0, false, false, false, 1);

    @Test
    void srgbRoundTrips() {
        for (float value = 0; value <= 1; value += 0.05f) {
            assertEquals(value, TextureViewer.toSrgb(TextureViewer.toLinear(value)), 1e-4f);
        }
        // Middle gray: 0.5 encoded is about 0.214 linear
        assertEquals(0.214f, TextureViewer.toLinear(0.5f), 0.001f);
    }

    @Test
    void averageIsWeightedByAlpha() {
        // A red opaque pixel and a green see-through one
        final ImageFile image = Swatch.image("mixed", new float[] {1, 0, 0, 1, 0, 1, 0, 0}, 2, 1);
        final float[] average = TextureViewer.average(image, false);
        assertNotNull(average);
        assertEquals(1, average[0], 1e-3f);
        assertEquals(0, average[1], 1e-3f);
        assertEquals(0.2126f, average[3], 1e-3f, "The luminance of pure red");
    }

    @Test
    void averageDecodesSrgb() {
        final ImageFile image = Swatch.image("gray", new float[] {0.5f, 0.5f, 0.5f, 1}, 1, 1);
        final float[] average = TextureViewer.average(image, true);
        assertNotNull(average);
        assertEquals(TextureViewer.toLinear(0.5f), average[0], 1e-3f);
        assertEquals(TextureViewer.toLinear(0.5f), average[3], 1e-3f);
    }

    @Test
    void averageOfNothingVisibleIsNull() {
        final ImageFile image = Swatch.image("clear", new float[] {1, 1, 1, 0}, 1, 1);
        assertNull(TextureViewer.average(image, false));
    }

    @Test
    void shadePicksAChannelAsGray() {
        final TextureViewer.Settings green =
                new TextureViewer.Settings(TextureView.Channel.GREEN, 0, false, false, false, 1);
        final int argb = TextureViewer.shade(new float[] {0.1f, 0.6f, 0.9f, 0.5f}, green);
        assertEquals(0xFF999999, argb, "Green 0.6 as opaque gray");
    }

    @Test
    void shadeBrightensLinearLightBeforeEncoding() {
        final TextureViewer.Settings hdr =
                new TextureViewer.Settings(TextureView.Channel.RGB, 1, false, true, false, 1);
        // 0.25 linear, one stop brighter, is 0.5 linear, which encodes to 188
        final int argb = TextureViewer.shade(new float[] {0.25f, 0.25f, 0.25f, 1}, hdr);
        assertEquals(188, argb & 0xFF);
    }

    @Test
    void tiledRenderRepeatsTheImage() {
        final ImageFile image = Swatch.image("corner", new float[] {1, 0, 0, 1, 0, 0, 1, 1}, 2, 1);
        final TextureViewer.Settings tiled =
                new TextureViewer.Settings(TextureView.Channel.RGBA, 0, false, false, false, 3);
        final BufferedImage rendered = TextureViewer.render(image, tiled);
        assertEquals(6, rendered.getWidth());
        assertEquals(3, rendered.getHeight());
        for (int y = 0; y < 3; ++y) {
            for (int x = 0; x < 6; x += 2) {
                assertEquals(0xFFFF0000, rendered.getRGB(x, y));
                assertEquals(0xFF0000FF, rendered.getRGB(x + 1, y));
            }
        }
    }

    @Test
    void renderRefusesHugeCaptures() {
        final int side = TextureViewer.MAX_CAPTURE_SIDE / 2 + 1;
        final ImageFile image = Swatch.image("wide", new float[side * ImageFile.CHANNELS], side, 1);
        final TextureViewer.Settings tiled =
                new TextureViewer.Settings(TextureView.Channel.RGBA, 0, false, false, false, 3);
        assertThrows(IllegalArgumentException.class, () -> TextureViewer.render(image, tiled));
    }

    @Test
    void renderedPngReadsBack() throws IOException {
        final ImageFile image = Swatch.image("half", new float[] {0.5f, 0.25f, 1, 1}, 1, 1);
        final Path file = folder.resolve("half.png");
        ImageIO.write(TextureViewer.render(image, RAW), "png", file.toFile());
        final BufferedImage read = ImageIO.read(file.toFile());
        assertEquals(0xFF8040FF, read.getRGB(0, 0));
    }
}
