package com.ikalagaming.graphics.asset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** Texture sections: mip levels made on write, and reading back what was written. */
class TextureSectionTest {
    /**
     * Pixels of one color.
     *
     * @param width The width.
     * @param height The height.
     * @param rgba The color's bytes.
     * @return The pixels.
     */
    private static byte[] filled(int width, int height, int... rgba) {
        final byte[] out = new byte[width * height * 4];
        for (int i = 0; i < out.length; ++i) {
            out[i] = (byte) rgba[i % 4];
        }
        return out;
    }

    @Test
    void levelsHalveDownToOnePixel() {
        assertEquals(6, TextureSection.levelCount(48, 16), "48x16, 24x8, 12x4, 6x2, 3x1, 1x1");
        assertEquals(5, TextureSection.levelCount(16, 16));
        final byte[] all =
                TextureSection.withMips(
                        TextureSection.Kind.COLOR, filled(48, 16, 10, 20, 30, 255), 48, 16);
        assertEquals((48 * 16 + 24 * 8 + 12 * 4 + 6 * 2 + 3 + 1) * 4, all.length);
        // A flat color stays the same at every level
        assertEquals(10, all[all.length - 4]);
        assertEquals((byte) 255, all[all.length - 1]);
    }

    @Test
    void colorsAverageInLinearLight() {
        final byte[] top = new byte[2 * 2 * 4];
        for (int i = 0; i < 4; ++i) {
            final int v = i % 2 == 0 ? 0 : 255;
            top[i * 4] = (byte) v;
            top[i * 4 + 1] = (byte) v;
            top[i * 4 + 2] = (byte) v;
            top[i * 4 + 3] = (byte) 255;
        }
        final byte[] color = TextureSection.withMips(TextureSection.Kind.COLOR, top, 2, 2);
        assertEquals(188, color[16] & 0xFF, "Half of white in light is sRGB 188, not 128");
        final byte[] orm = TextureSection.withMips(TextureSection.Kind.ORM, top, 2, 2);
        assertEquals(128, orm[16] & 0xFF, "Linear maps average their values");
    }

    @Test
    void normalsStayUnitLength() {
        // One normal tipped right, one tipped left: the average points straight out
        final byte[] top = {
            (byte) 218, (byte) 128, (byte) 218, (byte) 255, 38, (byte) 128, (byte) 218, (byte) 255
        };
        final byte[] all = TextureSection.withMips(TextureSection.Kind.NORMAL, top, 2, 1);
        assertEquals(128, all[8] & 0xFF, 1);
        assertEquals(255, all[10] & 0xFF, "Renormalized to point straight out");
    }

    @Test
    void textureFilesReadBackWhatWasWritten() {
        final byte[] color =
                TextureSection.withMips(
                        TextureSection.Kind.COLOR, filled(16, 8, 200, 100, 50, 255), 16, 8);
        final byte[] normal =
                TextureSection.withMips(
                        TextureSection.Kind.NORMAL, filled(16, 8, 128, 128, 255, 255), 16, 8);
        final TextureSection.Stored stored =
                new TextureSection.Stored(
                        16,
                        8,
                        TextureSection.levelCount(16, 8),
                        List.of(
                                new TextureSection.MapData(TextureSection.Kind.COLOR, color),
                                new TextureSection.MapData(TextureSection.Kind.NORMAL, normal)));
        final ByteBuffer file = TextureSection.file(stored, AssetMetadata.of("test", "1"));
        final TextureSection.Stored read = TextureSection.read(AssetFile.read(file));
        assertEquals(16, read.width());
        assertEquals(8, read.height());
        assertEquals(5, read.levels());
        assertArrayEquals(color, read.map(TextureSection.Kind.COLOR).orElseThrow().pixels());
        assertArrayEquals(normal, read.map(TextureSection.Kind.NORMAL).orElseThrow().pixels());
        assertTrue(read.map(TextureSection.Kind.ORM).isEmpty());
        assertArrayEquals(new int[] {0, 512, 640, 672, 680}, read.levelOffsets());
        assertEquals(16 * 8 * 4, read.topLevel(read.maps().getFirst()).length);
    }

    @Test
    void badTexturesAreRefused() {
        final TextureSection.Stored short1 =
                new TextureSection.Stored(
                        4,
                        4,
                        3,
                        List.of(
                                new TextureSection.MapData(
                                        TextureSection.Kind.COLOR, new byte[10])));
        assertThrows(IllegalArgumentException.class, () -> TextureSection.encode(short1));
        final byte[] ok = TextureSection.withMips(TextureSection.Kind.COLOR, new byte[64], 4, 4);
        final TextureSection.Stored twice =
                new TextureSection.Stored(
                        4,
                        4,
                        3,
                        List.of(
                                new TextureSection.MapData(TextureSection.Kind.COLOR, ok),
                                new TextureSection.MapData(TextureSection.Kind.COLOR, ok)));
        assertThrows(IllegalArgumentException.class, () -> TextureSection.encode(twice));
    }

    @Test
    void mapsOfUnknownKindsAreSkipped() {
        final byte[] ok = TextureSection.withMips(TextureSection.Kind.COLOR, new byte[16], 2, 2);
        final ByteBuffer payload =
                ByteBuffer.allocate(TextureSection.RECORD_SIZE + 2 * (8 + ok.length))
                        .order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(TextureSection.RECORD_SIZE).putInt(2).putInt(2).putInt(2).putInt(2);
        payload.putInt(99).putInt(ok.length).put(ok);
        payload.putInt(TextureSection.Kind.COLOR.id).putInt(ok.length).put(ok);
        payload.flip();
        final ByteBuffer file =
                new AssetWriter()
                        .metadata(AssetMetadata.of("test", "1"))
                        .section(TextureSection.TAG, TextureSection.VERSION, true, payload)
                        .toBuffer();
        final TextureSection.Stored read = TextureSection.read(AssetFile.read(file));
        assertEquals(1, read.maps().size());
        assertEquals(TextureSection.Kind.COLOR, read.maps().getFirst().kind());
    }
}
