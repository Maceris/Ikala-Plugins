package com.ikalagaming.graphics.frontend.gui.data;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.freetype.FreeType.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.launcher.PluginFolder;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4i;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Bitmap;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.*;

/** Handles all the loaded fonts, and the texture used to cache and render characters. */
@Slf4j
public class FontAtlas {
    private static class CacheElement {
        /** Previous element in the doubly-linked list. */
        public CacheElement previous;

        /** Next element in the doubly-linked list. */
        public CacheElement next;

        /** The next value in the case of collisions. */
        public CacheElement chain;

        /** The actual character value. */
        public final char value;

        public int x;
        public int y;
        public int width;
        public int height;

        public CacheElement(final char value) {
            this.previous = null;
            this.next = null;
            this.chain = null;
            this.value = value;
        }
    }

    /** Which bits of a linear allocator are empty. */
    @AllArgsConstructor
    private static class FreeBlock {
        private int position;
        private int size;
    }

    /** Horizontal strips of the texture, each of which acts like a 1-D allocator. */
    private static class Shelf {
        public final int y;
        public final int width;
        public final int height;
        private final List<FreeBlock> freeList;

        public Shelf(int y, int width, int height) {
            this.y = y;
            this.width = width;
            this.height = height;
            freeList = new LinkedList<>();
            freeList.add(new FreeBlock(0, width));
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

    /** Height of a font atlas image, in pixels. */
    public static final int FONT_ATLAS_IMAGE_HEIGHT = 2048;

    /** Width of a font atlas image, in pixels. */
    public static final int FONT_ATLAS_IMAGE_WIDTH = 2048;

    private PointerBuffer freeTypeLibrary;
    private final Map<String, Font> fonts;

    private CacheElement[] cacheElements;

    /** The newest part of the LRU list. Sentinel value, not an actual element. */
    private final CacheElement cacheHead;

    /** The oldest part of the LRU list. Sentinel value, not an actual element. */
    private final CacheElement cacheTail;

    /** Our allocator tracking. */
    private final List<Shelf> shelves;

    private final List<FreeBlock> shelvesFreeList;

    /**
     * Font bitmaps that we have loaded into memory, but not yet written to the texture on the GPU.
     */
    public List<StagedBitmap> stagedBitmaps;

    public FontAtlas() {
        cacheHead = new CacheElement(' ');
        cacheHead.x = -1;
        cacheHead.y = -1;
        cacheTail = new CacheElement(' ');
        cacheTail.x = -1;
        cacheTail.y = -1;

        cacheHead.next = cacheTail;
        cacheHead.previous = null;
        cacheTail.next = null;
        cacheTail.previous = cacheHead;
        cacheElements = new CacheElement[CACHE_SIZE];
        stagedBitmaps = new ArrayList<>();
        shelves = new ArrayList<>();
        shelvesFreeList = new ArrayList<>();
        shelvesFreeList.add(new FreeBlock(0, FONT_ATLAS_IMAGE_HEIGHT));

        fonts = new HashMap<>();
        freeTypeLibrary = PointerBuffer.allocateDirect(1);

        int error = FT_Init_FreeType(freeTypeLibrary);
        if (error != FT_Err_Ok) {
            throw new IllegalStateException(
                    "Failed to initialize FreeType: " + FT_Error_String(error));
        }

        try (MemoryStack stack = stackPush()) {
            IntBuffer major = stack.mallocInt(1);
            IntBuffer minor = stack.mallocInt(1);
            IntBuffer patch = stack.mallocInt(1);

            FT_Library_Version(freeTypeLibrary.get(0), major, minor, patch);
            log.debug("Loaded FreeType v{}.{}.{}", major.get(0), minor.get(0), patch.get(0));
        }
    }

    @Synchronized
    public void addDefaultCharacters(@NonNull String fontPath, int fontSize) {
        Font font = fonts.get(fontPath);
        if (font == null) {
            log.error("Trying to add default characters for unloaded font {}", fontPath);
            return;
        }
        // Add all the printable ascii characters
        for (char c = ' '; c <= '~'; ++c) {
            cacheAdd(font, c, fontSize);
        }
        // TODO(ches) add extended latin characters
    }

    /**
     * Add a character to the cache.
     *
     * @param font The font we are using.
     * @param c The character to add.
     * @param fontSize The size of the font we are adding.
     */
    private void cacheAdd(@NonNull Font font, char c, int fontSize) {
        final int hashValue = Objects.hash(font, c, fontSize);
        final int index = hashValue & CACHE_MASK;

        boolean exists = false;

        CacheElement element = cacheElements[index];
        while (element != null && element.value != c) {
            element = element.chain;
        }
        if (element != null) {
            exists = true;
        }

        if (exists) {
            moveToFront(element);
            return;
        }

        CacheElement newElement = new CacheElement(c);

        StagedBitmap newBitmap = loadGlyph(font, c, newElement, index);
        if (newBitmap == null) {
            return;
        }

        if (cacheElements[index] == null) {
            cacheElements[index] = newElement;
        } else {
            element = cacheElements[index];
            while (element.chain != null) {
                element = element.chain;
            }
            element.chain = newElement;
        }
        CacheElement next = cacheHead.next;
        cacheHead.next = newElement;
        newElement.next = next;
        next.previous = newElement;
    }

    private void cacheRemove(@NonNull CacheElement element, int cacheIndex) {
        // Drop it from cache array
        if (cacheElements[cacheIndex] == element) {
            if (element.chain != null) {
                cacheElements[cacheIndex] = element.chain;
            } else {
                cacheElements[cacheIndex] = null;
            }
        } else {
            CacheElement previous = cacheElements[cacheIndex];
            while (previous.chain != null && previous.chain != element) {
                previous = previous.chain;
            }
            if (previous.chain == null) {
                log.error("Trying to remove an element we can't find in the cache");
                return;
            }
            previous.chain = element.chain;
        }

        // Now drop it from the LRU list
        CacheElement previous = element.previous;
        CacheElement next = element.next;
        previous.next = next;
        next.previous = previous;

        // And clean up references in the element
        element.previous = null;
        element.next = null;
        element.chain = null;
    }

    /**
     * Free up any resources owned by the font atlas. This must be done when the atlas is no longer
     * needed, or there will be resource leaks.
     */
    public void destroy() {
        if (freeTypeLibrary == null) {
            log.error("Trying to destroy Font Atlas twice.");
            return;
        }
        fonts.forEach((k, v) -> v.destroy());
        fonts.clear();
        cacheElements = null;
        cacheHead.next = null;
        cacheTail.previous = null;
        stagedBitmaps.clear();
        shelves.clear();

        FT_Done_FreeType(freeTypeLibrary.get(0));
        freeTypeLibrary = null;
    }

    private void allocAtlasSpace(
            int width, int height, @NonNull CacheElement element, int cacheIndex) {
        if (width > FONT_ATLAS_IMAGE_WIDTH || height > FONT_ATLAS_IMAGE_HEIGHT) {
            log.error("Character too big to fit in the texture");
            return;
        }

        while (cacheTail.previous != cacheHead) {
            for (Shelf shelf : shelves) {
                if (shelf.height < height) {
                    continue;
                }
                for (FreeBlock block : shelf.freeList) {
                    if (block.size > width) {
                        // Found one
                        element.x = block.position;
                        element.y = shelf.y;
                        block.position += width;
                        block.size -= width;
                        return;
                    }
                }
            }
            // None of the shelves had space, we need a new one

            // We want a power of 2 height, so we tend to have some extra room to minimize extra
            // shelves
            int shelfHeight = 1;
            while (shelfHeight < height) {
                shelfHeight <<= 1;
            }

            for (FreeBlock block : shelvesFreeList) {
                if (block.size >= shelfHeight) {
                    shelves.add(new Shelf(block.position, FONT_ATLAS_IMAGE_WIDTH, shelfHeight));
                    block.position += shelfHeight;
                    block.size -= shelfHeight;
                    return;
                }
            }
            // There was no room for a new shelf, we will need to start clearing out old data

            CacheElement oldestElement = cacheTail.previous;
            Shelf oldShelf = null;
            for (Shelf shelf : shelves) {
                if (shelf.y == oldestElement.y) {
                    oldShelf = shelf;
                    break;
                }
            }
            if (oldShelf == null) {
                log.error(
                        "Cache is messed up, couldn't find the shelf the cache element belonged to");
                return;
            }

            if (oldestElement.width >= width && oldShelf.height >= height) {
                element.x = oldestElement.x;
                element.y = oldShelf.y;

                cacheRemove(oldestElement, cacheIndex);
                if (oldestElement.width > width) {
                    // It's smaller than the old element, so add in a sliver of empty space
                    FreeBlock newBlock =
                            new FreeBlock(element.x + width, oldestElement.width - width);
                    mergeBlock(oldShelf.freeList, newBlock);
                }
                break;
            }

            // it is not enough
            FreeBlock newBlock = new FreeBlock(oldestElement.x, oldestElement.width);
            mergeBlock(oldShelf.freeList, newBlock);

            if (oldShelf.freeList.size() == 1
                    && oldShelf.freeList.getFirst().size == oldShelf.width) {
                // We emptied the shelf
                FreeBlock shelfBlock = new FreeBlock(oldShelf.y, oldShelf.height);
                mergeBlock(shelvesFreeList, shelfBlock);
                shelves.remove(oldShelf);
            }
            // Back to the top, I guess.
        }
    }

    private void mergeBlock(@NonNull List<FreeBlock> freeList, @NonNull FreeBlock newBlock) {
        int lastSmallerIndex = 0;
        for (int i = 0; i < freeList.size(); ++i) {
            FreeBlock block = freeList.get(i);

            if (block.position == newBlock.position + newBlock.size) {
                // it's right before an empty block, so just stretch the block a bit left
                block.position -= newBlock.size;
                if (i > 0) {
                    FreeBlock previousBlock = freeList.get(i - 1);
                    if (previousBlock.position + previousBlock.size == block.position) {
                        // We just mashed the current block up against the previous one
                        previousBlock.size += block.size;
                        // So merge them and drop the current one
                        freeList.remove(i);
                    }
                }
                return;
            }
            if (block.position + block.size == newBlock.position) {
                // it's right after an empty block, so stretch the current block a bit right
                block.size += newBlock.size;

                if (i + 1 < freeList.size()) {
                    FreeBlock nextBlock = freeList.get(i + 1);
                    if (nextBlock.position == block.position + block.size) {
                        // We just mashed the current block up against the next one
                        block.size += nextBlock.size;
                        // So merge them and drop the next one
                        freeList.remove(i + 1);
                    }
                }
                return;
            }

            if (block.position > newBlock.position) {
                // We passed where this would need to go, so bail out and add after the last block
                // we saw
                break;
            }
            lastSmallerIndex = i;
        }

        // It's disconnected, just slap in a new block
        freeList.add(lastSmallerIndex, newBlock);
    }

    /**
     * Fetch a font. Will be null if the font is not loaded.
     *
     * @param fontPath The path to the font in the plugin data folder.
     * @return The font, if loaded. May be null.
     */
    public Font getFont(@NonNull String fontPath) {
        return fonts.get(fontPath);
    }

    @Synchronized
    public boolean getFontMapPosition(@NonNull Font font, char c, int fontSize, Vector4i output) {
        final int hashValue = Objects.hash(font, c, fontSize);
        final int index = hashValue & CACHE_MASK;

        CacheElement element = cacheElements[index];
        while (element != null && element.value != c) {
            element = element.chain;
        }
        if (element == null) {
            return false;
        }
        moveToFront(element);
        output.set(element.x, element.y, element.width, element.height);
        return true;
    }

    /**
     * Check if a font is loaded.
     *
     * @param fontPath The path to the font in the plugin data folder.
     * @return Whether the font is currently loaded.
     */
    public boolean isFontLoaded(@NonNull String fontPath) {
        return fonts.containsKey(fontPath);
    }

    /**
     * Load a font. Should only be called once per font as subsequent attempts will be ignored,
     * unless it's unloaded.
     *
     * @param fontPath The path to the font in the plugin data folder. This is treated as the name
     *     of the font.
     * @return Whether the font is currently loaded successfully.
     */
    @Synchronized
    public boolean loadFont(@NonNull String fontPath) {
        if (fonts.containsKey(fontPath)) {
            log.warn("Font {} already loaded, trying to load twice.", fontPath);
            return true;
        }

        try {
            File fontFile =
                    PluginFolder.getResource(
                            GraphicsPlugin.PLUGIN_NAME, PluginFolder.ResourceType.DATA, fontPath);

            if (!fontFile.canRead()) {
                log.error("Cannot read font file from data folder: {}", fontPath);
                return false;
            }

            byte[] fontData = Files.readAllBytes(fontFile.toPath());

            ByteBuffer fontByteBuffer = ByteBuffer.allocateDirect(fontData.length);
            fontByteBuffer.put(fontData);
            fontByteBuffer.flip();

            PointerBuffer fontPointer = PointerBuffer.allocateDirect(1);

            int error = FT_New_Memory_Face(freeTypeLibrary.get(0), fontByteBuffer, 0, fontPointer);
            if (error != FT_Err_Ok) {
                log.error("Failed to create memory face: {} ({})", FT_Error_String(error), error);
                return false;
            }

            Font font = new Font(fontPath, fontByteBuffer, fontPointer);

            fonts.put(fontPath, font);
        } catch (IOException e) {
            log.error("Error reading font file: {}", fontPath);
            return false;
        }

        return true;
    }

    private StagedBitmap loadGlyph(
            @NonNull Font font, char c, @NonNull CacheElement element, int cacheIndex) {
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

            allocAtlasSpace(width, height, element, cacheIndex);
            element.width = width;
            element.height = height;

            StagedBitmap newBitmap =
                    new StagedBitmap(newContents, element.x, element.y, width, height);
            stagedBitmaps.add(newBitmap);

            return newBitmap;

        } finally {
            font.lock.unlock();
        }
    }

    private void moveToFront(@NonNull CacheElement element) {
        CacheElement previous = element.previous;
        if (previous == cacheHead) {
            // already at the front
            return;
        }
        CacheElement next = element.next;
        previous.next = next;
        next.previous = previous;

        element.next = cacheHead.next;
        element.previous = cacheHead;
        cacheHead.next.previous = element;
        cacheHead.next = element;
    }

    @Synchronized
    public void registerCharacter(@NonNull String fontPath, char c, int fontSize) {
        Font font = fonts.get(fontPath);
        if (font == null) {
            log.error("Trying to register character for unloaded font {}", fontPath);
            return;
        }
        cacheAdd(font, c, fontSize);
    }

    /**
     * Unload a font, cleaning up its resources. This will pull the font out of the context structs
     * if it's in use.
     *
     * @param fontPath The path to the font in the plugin data folder.
     */
    public void unloadFont(@NonNull String fontPath) {
        Font font = fonts.get(fontPath);
        if (font == null) {
            log.warn("Font {} not found when trying to destroy it in the font atlas", fontPath);
            return;
        }
        Context context = IkGui.getContext();
        if (context.font != null && context.font.name.equals(fontPath)) {
            context.font = null;
        }
        context.fontFallbacks.removeIf(currentFont -> currentFont.name.equals(fontPath));

        fonts.remove(fontPath);
        font.destroy();
    }
}
