package com.ikalagaming.graphics.frontend.gui.data;

import static org.lwjgl.util.freetype.FreeType.*;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.util.freetype.FT_Bitmap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An LRU cache of font data used for rendering. */
@Slf4j
public class FontCache {
    public static class CacheElement {
        /** Previous element in the doubly-linked list. */
        public CacheElement previous;

        /** Next element in the doubly-linked list. */
        public CacheElement next;

        /** The next value in the case of collisions. */
        public CacheElement chain;

        /** The actual character value. */
        public final char value;

        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public CacheElement(
                final char value, final int x, final int y, final int width, final int height) {
            this.previous = null;
            this.next = null;
            this.chain = null;
            this.value = value;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * A font bitmap that is staged for loading into the font texture.
     *
     * @param data The bitmap data, as RGBA32 pixels.
     * @param x The x location in the font atlas texture.
     * @param y The y location in the font atlas texture.
     * @param width The width, in pixels.
     * @param height The height, in pixels.
     */
    public record StagedBitmap(ByteBuffer data, int x, int y, int width, int height) {}

    public static int hashCharacter(@NonNull String fontName, char c, int fontSize) {
        // TODO(ches) hash, probably also include multi-characters in hash
        return 0;
    }

    /**
     * Given 12pt font characters are roughly 16x16px, this is how many would fit in a 2048x2048
     * texture. It's important this number be a power of 2 size, so we can just lop off bits from a
     * hash to get the cache value. Larger entries might not fit in the texture (larger font,
     * combined ligatures), so this is only the size of the lookup table, not how many characters
     * fit in the atlas. That has to be determined by actually looking for space.
     */
    private static final int CACHE_SIZE = 1 << 14;

    /** A mask for the cache index bits. Relies on CACHE_SIZE being a power of 2. */
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    /** Used if characters don't have a visual representation. */
    private static final StagedBitmap EMPTY_BITMAP = new StagedBitmap(null, 0, 0, 0, 0);

    private int size;
    private CacheElement[] hash;

    /** The newest part of the list. Sentinel value, not an actual element. */
    private final CacheElement head;

    /** The oldest part of the list. Sentinel value, not an actual element. */
    private final CacheElement tail;

    /**
     * Font bitmaps that we have loaded into memory, but not yet written to the texture on the GPU.
     */
    public List<StagedBitmap> stagedBitmaps;

    public FontCache() {
        head = new CacheElement(' ', -1, -1, 0, 0);
        tail = new CacheElement(' ', -1, -1, 0, 0);

        head.next = tail;
        head.previous = null;
        tail.next = null;
        tail.previous = head;
        size = 0;
        hash = new CacheElement[CACHE_SIZE];
        stagedBitmaps = new ArrayList<>();
    }

    /**
     * Add a character to the cache.
     *
     * @param font The font we are using.
     * @param c The character to add.
     * @param fontSize The size of the font we are adding.
     * @return True if it is freshly added, false if it was already in the cache.
     */
    public boolean add(@NonNull Font font, char c, int fontSize) {
        final int hashValue = Objects.hash(font, c, fontSize);
        final int index = hashValue & CACHE_MASK;

        boolean exists = false;

        CacheElement element = hash[index];
        while (element != null && element.value != c) {
            element = element.chain;
        }
        if (element != null) {
            exists = true;
        }

        if (exists) {
            moveToFront(element);
            return true;
        }

        StagedBitmap newBitmap = loadGlyph(font, c);
        if (newBitmap == null) {
            return false;
        }

        CacheElement newElement =
                new CacheElement(c, newBitmap.x, newBitmap.y, newBitmap.width, newBitmap.height);

        if (hash[index] == null) {
            hash[index] = newElement;
        } else {
            element = hash[index];
            while (element.chain != null) {
                element = element.chain;
            }
            element.chain = newElement;
        }
        CacheElement next = head.next;
        head.next = newElement;
        newElement.next = next;
        next.previous = newElement;

        return true;
    }

    public void destroy() {
        // TODO(ches) clean up
        hash = null;
        head.next = null;
        tail.previous = null;
        stagedBitmaps.clear();
    }

    private StagedBitmap loadGlyph(@NonNull Font font, char c) {
        font.lock.lock();
        try {
            int glyphIndex = FT_Get_Char_Index(font.face, c);
            int error = FT_Load_Glyph(font.face, glyphIndex, FT_LOAD_DEFAULT);
            if (error != FT_Err_Ok) {
                log.error("Failed to load char {} for font {}", c, font.name);
                return null;
            }
            if (Objects.requireNonNull(font.face.glyph()).format() != FT_GLYPH_FORMAT_BITMAP) {
                error =
                        FT_Render_Glyph(
                                Objects.requireNonNull(font.face.glyph()), FT_RENDER_MODE_NORMAL);
                if (error != FT_Err_Ok) {
                    log.error("Failed to render char {} for font {}", c, font.name);
                    return null;
                }
            }

            FT_Bitmap bitmap = Objects.requireNonNull(font.face.glyph()).bitmap();
            final int width = bitmap.width();
            final int height = bitmap.rows();
            final int numGrays = bitmap.num_grays();
            final int totalPixels = width * height;
            int originalBufferSize = Math.abs(bitmap.pitch()) * height;

            ByteBuffer oldContents = bitmap.buffer(originalBufferSize);
            if (oldContents == null) {
                return EMPTY_BITMAP;
            }
            ByteBuffer newContents = ByteBuffer.allocateDirect(width * height * 4);

            int pixelsProcessed = 0;
            switch (bitmap.pixel_mode()) {
                case FT_PIXEL_MODE_MONO:
                    for (int i = 0; i < originalBufferSize; ++i) {
                        byte currentByte = oldContents.get(i);

                        int bitsToProcess = Math.min(8, totalPixels - pixelsProcessed);
                        for (int j = 0; j < bitsToProcess; ++j) {
                            int value = (currentByte >> j) & 0b1;
                            int newPixel = value == 1 ? 0xFFFFFFFF : 0x00000000;
                            newContents.putInt(newPixel);
                        }
                        pixelsProcessed += 8;
                    }
                    break;
                case FT_PIXEL_MODE_GRAY2:
                    for (int i = 0; i < originalBufferSize; ++i) {
                        byte currentByte = oldContents.get(i);

                        int bitsToProcess = Math.min(8, (totalPixels - pixelsProcessed) * 2);
                        for (int j = 0; j < bitsToProcess; j += 2) {
                            int value = (currentByte >> j) & 0b11;
                            value = (255 * value) / 4;
                            int newPixel = (value << 24) | (value << 16) | (value << 8) | value;
                            newContents.putInt(newPixel);
                        }
                        pixelsProcessed += 4;
                    }
                    break;
                case FT_PIXEL_MODE_GRAY4:
                    for (int i = 0; i < originalBufferSize; ++i) {
                        byte currentByte = oldContents.get(i);

                        int bitsToProcess = Math.min(8, (totalPixels - pixelsProcessed) * 4);
                        for (int j = 0; j < bitsToProcess; j += 4) {
                            int value = (currentByte >> j) & 0b1111;
                            value = (255 * value) / 16;
                            int newPixel = (value << 24) | (value << 16) | (value << 8) | value;
                            newContents.putInt(newPixel);
                        }
                        pixelsProcessed += 2;
                    }
                    break;
                case FT_PIXEL_MODE_GRAY:
                    for (int i = 0; i < originalBufferSize; ++i) {
                        byte currentByte = oldContents.get(i);
                        int value = (currentByte * 255) / numGrays;
                        int newPixel = (value << 24) | (value << 16) | (value << 8) | value;
                        newContents.putInt(newPixel);
                    }
                    break;
                case FT_PIXEL_MODE_LCD, FT_PIXEL_MODE_LCD_V:
                    for (int i = 0; i + 2 < originalBufferSize; i += 3) {
                        byte rAlpha = oldContents.get(i);
                        byte gAlpha = oldContents.get(i + 1);
                        byte bAlpha = oldContents.get(i + 2);
                        int averageAlpha = (rAlpha + gAlpha + bAlpha) / 3;

                        int newPixel =
                                (rAlpha << 24) | (gAlpha << 16) | (bAlpha << 8) | averageAlpha;
                        newContents.putInt(newPixel);
                    }
                    break;
                case FT_PIXEL_MODE_BGRA:
                    for (int i = 0; i + 3 < originalBufferSize; i += 4) {
                        byte b = oldContents.get(i);
                        byte g = oldContents.get(i + 1);
                        byte r = oldContents.get(i + 2);
                        byte a = oldContents.get(i + 3);

                        int newPixel = (r << 24) | (g << 16) | (b << 8) | a;
                        newContents.putInt(newPixel);
                    }
                    break;
                default:
                    log.error(
                            "Unexpected pixel mode {} for font {}", bitmap.pixel_mode(), font.name);
                    return null;
            }
            newContents.flip();

            // TODO(ches) find a position in the texture
            int x = 0;
            int y = 0;

            StagedBitmap newBitmap = new StagedBitmap(newContents, x, y, width, height);
            stagedBitmaps.add(newBitmap);

            return newBitmap;

        } finally {
            font.lock.unlock();
        }
    }

    private void moveToFront(CacheElement element) {
        // TODO(ches) do this
    }

    private void remove(CacheElement element) {
        // TODO(ches) do this
    }
}
