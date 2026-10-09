package com.ikalagaming.graphics.gui.data;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.freetype.FreeType.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.launcher.PluginFolder;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.*;

/** Handles all the loaded fonts, and the texture used to cache and render characters. */
@Slf4j
public class FontAtlas {

    public static class CharInfo {
        /** The actual character value. */
        public final char value;

        public int x;
        public int y;
        public int width;
        public int height;
        public int bearingX;
        public int bearingY;

        /** How far to move the pen to the right after this character, in pixels. */
        public float advance;

        public CharInfo(final char value) {
            this.value = value;
        }
    }

    private static class CacheElement {
        /** Previous element in the doubly-linked list. */
        public CacheElement previous;

        /** Next element in the doubly-linked list. */
        public CacheElement next;

        /** The next value in the case of collisions. */
        public CacheElement chain;

        public final CharInfo info;

        /** The font the glyph was rendered with, null for the list sentinels. */
        public final Font font;

        /** The font size the glyph was rendered at. */
        public final int fontSize;

        /** The index in the cache array that the chain containing this element is stored at. */
        public final int cacheIndex;

        /**
         * Whether the glyph has space allocated in the texture, which needs to be freed when it's
         * evicted. Glyphs without a bitmap, like spaces, don't.
         */
        public boolean hasAtlasSpace;

        public CacheElement(Font font, final char value, int fontSize, int cacheIndex) {
            this.previous = null;
            this.next = null;
            this.chain = null;
            // TODO(ches) we should probably allocate these from an array instead of randomly on the
            // heap
            this.info = new CharInfo(value);
            this.font = font;
            this.fontSize = fontSize;
            this.cacheIndex = cacheIndex;
            this.hasAtlasSpace = false;
        }

        /**
         * Check if this is the glyph for a character in a font and size.
         *
         * @param font The font.
         * @param value The character.
         * @param fontSize The font size.
         * @return True if this element holds that glyph.
         */
        public boolean matches(Font font, char value, int fontSize) {
            return this.font == font && info.value == value && this.fontSize == fontSize;
        }
    }

    /**
     * The font that will be used to display a character, and the character to display, which is the
     * fallback character if no font supports the requested one.
     *
     * @param font The font.
     * @param value The character to display.
     */
    private record ResolvedChar(Font font, char value) {}

    /**
     * How many spaces wide a tab is. Fonts generally don't have a tab glyph, so like Dear ImGui we
     * draw tabs as a wide space.
     */
    public static final int TAB_SIZE = 4;

    /**
     * Check if a character is whitespace that fonts don't have glyphs for, which we draw as a space
     * of some width. Tabs are a wide space, and line breaks have no width for text that isn't split
     * into lines, like Dear ImGui skipping carriage returns.
     *
     * @param c The character.
     * @return True if the character is drawn using the space glyph.
     */
    private static boolean isSpecialWhitespace(char c) {
        return c == '\t' || c == '\n' || c == '\r';
    }

    /** Which bits of a linear allocator are empty. */
    @AllArgsConstructor
    static class FreeBlock {
        int position;
        int size;
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
            freeList = new ArrayList<>();
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

    /** A mask for the cache index bits. Relies on the cache size being a power of 2. */
    private final int cacheMask;

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

    /** Used for calculating kerning. */
    private FT_Vector kerningSize;

    /** The version of FreeType that was loaded, like "2.13.3". */
    private String freeTypeVersion = "unknown";

    /** Our allocator tracking. */
    private final List<Shelf> shelves;

    private final List<FreeBlock> shelvesFreeList;

    /**
     * Font bitmaps that we have loaded into memory, but not yet written to the texture on the GPU.
     */
    public List<StagedBitmap> stagedBitmaps;

    /**
     * The texture the atlas is uploaded to, set by the rendering backend. Only used by debug tools
     * to display the atlas, may be null.
     */
    public TextureHandle texture;

    public FontAtlas() {
        this(CACHE_SIZE);
    }

    /**
     * Create a font atlas with a specific number of slots in the lookup table, so tests can force
     * collisions.
     *
     * @param cacheSize The number of slots, which must be a power of 2.
     */
    FontAtlas(int cacheSize) {
        if (cacheSize <= 0 || Integer.bitCount(cacheSize) != 1) {
            throw new IllegalArgumentException("The cache size must be a power of 2");
        }
        cacheMask = cacheSize - 1;
        cacheHead = new CacheElement(null, ' ', 0, -1);
        cacheHead.info.x = -1;
        cacheHead.info.y = -1;
        cacheTail = new CacheElement(null, ' ', 0, -1);
        cacheTail.info.x = -1;
        cacheTail.info.y = -1;

        cacheHead.next = cacheTail;
        cacheHead.previous = null;
        cacheTail.next = null;
        cacheTail.previous = cacheHead;
        cacheElements = new CacheElement[cacheSize];
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
            freeTypeVersion = major.get(0) + "." + minor.get(0) + "." + patch.get(0);
            log.debug("Loaded FreeType v{}", freeTypeVersion);
        }
        this.kerningSize = FT_Vector.create();
    }

    @Synchronized
    public void addDefaultCharacters(@NonNull String fontPath, int fontSize) {
        Font font = fonts.get(fontPath);
        if (font == null) {
            IkGuiInternal.reportError(
                    log, "Trying to add default characters for unloaded font {}", fontPath);
            return;
        }
        // Add all the printable ascii characters
        for (char c = ' '; c <= '~'; ++c) {
            cacheAdd(font, c, fontSize);
        }
        // TODO(ches) add extended latin characters
    }

    /**
     * Calculate which slot of the cache array a glyph is stored in.
     *
     * @param font The font.
     * @param c The character.
     * @param fontSize The font size.
     * @return The index in the cache array.
     */
    private int cacheIndex(@NonNull Font font, char c, int fontSize) {
        return Objects.hash(font, c, fontSize) & cacheMask;
    }

    /**
     * Find a glyph in the cache.
     *
     * @param font The font.
     * @param c The character.
     * @param fontSize The font size.
     * @return The element, or null if it is not cached.
     */
    private CacheElement cacheFind(@NonNull Font font, char c, int fontSize) {
        CacheElement element = cacheElements[cacheIndex(font, c, fontSize)];
        while (element != null && !element.matches(font, c, fontSize)) {
            element = element.chain;
        }
        return element;
    }

    /**
     * Add a character to the cache.
     *
     * @param font The font we are using.
     * @param c The character to add.
     * @param fontSize The size of the font we are adding.
     */
    private void cacheAdd(@NonNull Font font, char c, int fontSize) {
        final CacheElement existing = cacheFind(font, c, fontSize);
        if (existing != null) {
            moveToFront(existing);
            return;
        }

        final int index = cacheIndex(font, c, fontSize);
        final CacheElement newElement = new CacheElement(font, c, fontSize, index);

        font.setSize(fontSize);
        StagedBitmap newBitmap = loadGlyph(font, c, newElement);

        if (newBitmap == null) {
            log.warn("Could not create a glyph for '{}'", c);
            return;
        }

        // Allocating space may have evicted elements, so look up the chain again now
        if (cacheElements[index] == null) {
            cacheElements[index] = newElement;
        } else {
            CacheElement element = cacheElements[index];
            while (element.chain != null) {
                element = element.chain;
            }
            element.chain = newElement;
        }
        CacheElement next = cacheHead.next;
        cacheHead.next = newElement;
        newElement.next = next;
        newElement.previous = cacheHead;
        next.previous = newElement;
    }

    /**
     * Remove an element from the cache array and LRU list. This does not free its atlas space, see
     * {@link #evict(CacheElement)}.
     *
     * @param element The element to remove.
     */
    private void cacheRemove(@NonNull CacheElement element) {
        final int cacheIndex = element.cacheIndex;
        // Drop it from cache array
        if (cacheElements[cacheIndex] == element) {
            cacheElements[cacheIndex] = element.chain;
        } else {
            CacheElement previous = cacheElements[cacheIndex];
            while (previous != null && previous.chain != element) {
                previous = previous.chain;
            }
            if (previous == null) {
                IkGuiInternal.reportError(
                        log, "Trying to remove an element we can't find in the cache");
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
     * Remove an element from the cache, and free the space it used in the texture.
     *
     * @param element The element to remove.
     */
    private void evict(@NonNull CacheElement element) {
        if (element.hasAtlasSpace) {
            freeAtlasSpace(element);
        }
        cacheRemove(element);
    }

    /**
     * Fetch the version of FreeType used to load fonts, for debugging.
     *
     * @return The version, like "2.13.3".
     */
    public String getFreeTypeVersion() {
        return freeTypeVersion;
    }

    /**
     * Check if the atlas was destroyed, for tests.
     *
     * @return True if destroy() was called.
     */
    boolean isDestroyed() {
        return freeTypeLibrary == null;
    }

    /**
     * Free up any resources owned by the font atlas. This must be done when the atlas is no longer
     * needed, or there will be resource leaks.
     */
    public void destroy() {
        if (freeTypeLibrary == null) {
            IkGuiInternal.reportError(log, "Trying to destroy Font Atlas twice.");
            return;
        }
        fonts.forEach((k, v) -> v.destroy());
        fonts.clear();
        cacheElements = null;
        cacheHead.next = null;
        cacheTail.previous = null;
        stagedBitmaps.clear();
        shelves.clear();
        // Allocated with create(), so the garbage collector frees it, calling free() would
        // corrupt the heap
        kerningSize = null;

        FT_Done_FreeType(freeTypeLibrary.get(0));
        freeTypeLibrary = null;
    }

    /**
     * Find space in the texture for a glyph, evicting the least recently used glyphs until there is
     * enough room.
     *
     * @param width The width of the glyph.
     * @param height The height of the glyph.
     * @param element The element to store the location in.
     * @return True if space was found, false if the glyph can't fit.
     */
    private boolean allocAtlasSpace(int width, int height, @NonNull CacheElement element) {
        if (width > FONT_ATLAS_IMAGE_WIDTH || height > FONT_ATLAS_IMAGE_HEIGHT) {
            IkGuiInternal.reportError(log, "Character too big to fit in the texture");
            return false;
        }

        while (!allocFromShelves(width, height, element)) {
            // There was no room, we will need to start clearing out old data
            final CacheElement oldestElement = cacheTail.previous;
            if (oldestElement == cacheHead) {
                IkGuiInternal.reportError(
                        log, "No room in the font atlas for a {}x{} character", width, height);
                return false;
            }
            evict(oldestElement);
        }
        element.hasAtlasSpace = true;
        return true;
    }

    /**
     * Try to find space for a glyph in the existing shelves, or a new shelf, without evicting
     * anything.
     *
     * @param width The width of the glyph.
     * @param height The height of the glyph.
     * @param element The element to store the location in.
     * @return True if space was found.
     */
    private boolean allocFromShelves(int width, int height, @NonNull CacheElement element) {
        for (Shelf shelf : shelves) {
            if (shelf.height < height) {
                continue;
            }
            for (int i = 0; i < shelf.freeList.size(); ++i) {
                final FreeBlock block = shelf.freeList.get(i);
                if (block.size >= width) {
                    // Found one
                    element.info.x = block.position;
                    element.info.y = shelf.y;
                    takeFromBlock(shelf.freeList, i, width);
                    return true;
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

        for (int i = 0; i < shelvesFreeList.size(); ++i) {
            final FreeBlock block = shelvesFreeList.get(i);
            if (block.size >= shelfHeight) {
                Shelf newShelf = new Shelf(block.position, FONT_ATLAS_IMAGE_WIDTH, shelfHeight);
                shelves.add(newShelf);
                takeFromBlock(shelvesFreeList, i, shelfHeight);

                element.info.x = 0;
                element.info.y = newShelf.y;
                takeFromBlock(newShelf.freeList, 0, width);
                return true;
            }
        }
        return false;
    }

    /**
     * Take space from the start of a free block, removing the block if it's used up.
     *
     * @param freeList The list the block is in.
     * @param index The index of the block.
     * @param size How much space to take.
     */
    private static void takeFromBlock(@NonNull List<FreeBlock> freeList, int index, int size) {
        final FreeBlock block = freeList.get(index);
        block.position += size;
        block.size -= size;
        if (block.size <= 0) {
            freeList.remove(index);
        }
    }

    /**
     * Free the texture space used by a glyph, and the shelf it was on if that is now empty.
     *
     * @param element The element to free the space of.
     */
    private void freeAtlasSpace(@NonNull CacheElement element) {
        element.hasAtlasSpace = false;
        Shelf shelf = null;
        for (Shelf potential : shelves) {
            if (potential.y == element.info.y) {
                shelf = potential;
                break;
            }
        }
        if (shelf == null) {
            IkGuiInternal.reportError(
                    log,
                    "Cache is messed up, couldn't find the shelf the cache element belonged to");
            return;
        }
        mergeBlock(shelf.freeList, new FreeBlock(element.info.x, element.info.width));

        if (shelf.freeList.size() == 1 && shelf.freeList.getFirst().size == shelf.width) {
            // We emptied the shelf
            mergeBlock(shelvesFreeList, new FreeBlock(shelf.y, shelf.height));
            shelves.remove(shelf);
        }
    }

    /**
     * Add a block of free space to a free list, which is sorted by position, joining it with any
     * neighboring blocks.
     *
     * @param freeList The free list.
     * @param newBlock The space that is now free.
     */
    static void mergeBlock(@NonNull List<FreeBlock> freeList, @NonNull FreeBlock newBlock) {
        if (newBlock.size <= 0) {
            return;
        }
        int index = 0;
        while (index < freeList.size() && freeList.get(index).position < newBlock.position) {
            ++index;
        }
        final FreeBlock previous = index > 0 ? freeList.get(index - 1) : null;
        final FreeBlock next = index < freeList.size() ? freeList.get(index) : null;
        final boolean joinsPrevious =
                previous != null && previous.position + previous.size == newBlock.position;
        final boolean joinsNext =
                next != null && newBlock.position + newBlock.size == next.position;

        if (joinsPrevious && joinsNext) {
            // It fills the gap between two blocks, so they become one
            previous.size += newBlock.size + next.size;
            freeList.remove(index);
        } else if (joinsPrevious) {
            previous.size += newBlock.size;
        } else if (joinsNext) {
            next.position = newBlock.position;
            next.size += newBlock.size;
        } else {
            freeList.add(index, newBlock);
        }
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

    /**
     * Fetch the position of a character in the font map, deducing the font based on which font
     * supports it. We start looking at the current font, then font fallbacks in order if not
     * supported. If a font is not found, we will use the fallback character.
     *
     * @param c The character to look up.
     * @param fontSize The font size.
     * @return An optional that contains the character info if we found it.
     */
    @Synchronized
    public Optional<CharInfo> getFontMapInfo(char c, int fontSize) {
        final ResolvedChar resolved = resolveFont(c);
        if (resolved == null) {
            return Optional.empty();
        }
        final CacheElement element = cacheFind(resolved.font(), resolved.value(), fontSize);
        if (element == null) {
            return Optional.empty();
        }
        moveToFront(element);
        return Optional.of(element.info);
    }

    /**
     * Figure out which font displays a character. We start looking at the current font, then font
     * fallbacks in order if not supported. If no font supports it, we use the fallback character in
     * the last fallback font, or the current font if there are no fallbacks.
     *
     * @param c The character.
     * @return The font and character to display, or null if there are no fonts.
     */
    private ResolvedChar resolveFont(char c) {
        if (isSpecialWhitespace(c)) {
            // Drawn as spaces, so use whichever font has a space
            final ResolvedChar space = resolveFont(' ');
            return space == null ? null : new ResolvedChar(space.font(), c);
        }
        final Context context = IkGui.getContext();
        final Font primary = context.font;
        if (primary != null && primary.supports(c)) {
            return new ResolvedChar(primary, c);
        }
        for (Font potential : context.fontFallbacks) {
            if (potential.supports(c)) {
                return new ResolvedChar(potential, c);
            }
        }
        // Nothing supports it
        final Font last =
                context.fontFallbacks.isEmpty() ? primary : context.fontFallbacks.getLast();
        return last == null ? null : new ResolvedChar(last, context.fontFallbackChar);
    }

    /**
     * Fetch the vertical metrics used to lay out text at a size. These come from the current font,
     * or the first fallback font if there is no current font, since glyphs from fallback fonts
     * share the baseline of the line they are on.
     *
     * @param fontSize The font size.
     * @return The metrics, or null if there are no fonts to use.
     */
    @Synchronized
    public FontMetrics getFontMetrics(int fontSize) {
        Font font = IkGui.getContext().font;
        if (font == null && !IkGui.getContext().fontFallbacks.isEmpty()) {
            font = IkGui.getContext().fontFallbacks.getFirst();
        }
        return font == null ? null : font.getMetrics(fontSize);
    }

    /**
     * Fetch the vertical metrics of a specific font at a size.
     *
     * @param fontName The name of the font.
     * @param fontSize The font size.
     * @return The metrics, or null if the font is not loaded.
     */
    @Synchronized
    public FontMetrics getFontMetrics(@NonNull String fontName, int fontSize) {
        final Font font = fonts.get(fontName);
        return font == null ? null : font.getMetrics(fontSize);
    }

    @Synchronized
    public float getKerning(char first, char second, int fontSize) {
        final ResolvedChar resolvedFirst = resolveFont(first);
        final ResolvedChar resolvedSecond = resolveFont(second);
        if (resolvedFirst == null
                || resolvedSecond == null
                || resolvedFirst.font() != resolvedSecond.font()
                || !resolvedFirst.font().supportsKerning) {
            // Kerning only applies to pairs of characters from the same font
            return 0.0f;
        }
        final Font font = resolvedFirst.font();
        font.lock.lock();
        try {
            font.setSize(fontSize);
            final int firstIndex = FT_Get_Char_Index(font.face, resolvedFirst.value());
            final int secondIndex = FT_Get_Char_Index(font.face, resolvedSecond.value());

            // Scaled to the current size and grid-fitted to whole pixels
            final int error =
                    FT_Get_Kerning(
                            font.face, firstIndex, secondIndex, FT_KERNING_DEFAULT, kerningSize);
            if (error != FT_Err_Ok) {
                IkGuiInternal.reportError(
                        log,
                        "Failed to fetch kerning for font {}: {}",
                        font.name,
                        FT_Error_String(error));
                return 0.0f;
            }
            // In 26.6 pixel format (i.e. 1/64 of a pixel)
            return kerningSize.x() / 64.0f;
        } finally {
            font.lock.unlock();
        }
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
                IkGuiInternal.reportError(
                        log, "Cannot read font file from data folder: {}", fontPath);
                return false;
            }

            return loadFont(fontPath, Files.readAllBytes(fontFile.toPath()));
        } catch (IOException e) {
            IkGuiInternal.reportError(log, "Error reading font file: {}", fontPath);
            return false;
        }
    }

    /**
     * Load a font from data that was already read, e.g. from a classpath resource. Should only be
     * called once per font as subsequent attempts will be ignored, unless it's unloaded.
     *
     * @param fontName The name of the font, used to refer to it later.
     * @param fontData The contents of the font file.
     * @return Whether the font is currently loaded successfully.
     */
    @Synchronized
    public boolean loadFont(@NonNull String fontName, byte @NonNull [] fontData) {
        if (fonts.containsKey(fontName)) {
            log.warn("Font {} already loaded, trying to load twice.", fontName);
            return true;
        }

        ByteBuffer fontByteBuffer = ByteBuffer.allocateDirect(fontData.length);
        fontByteBuffer.put(fontData);
        fontByteBuffer.flip();

        PointerBuffer fontPointer = PointerBuffer.allocateDirect(1);

        int error = FT_New_Memory_Face(freeTypeLibrary.get(0), fontByteBuffer, 0, fontPointer);
        if (error != FT_Err_Ok) {
            IkGuiInternal.reportError(
                    log, "Failed to create memory face: {} ({})", FT_Error_String(error), error);
            return false;
        }

        Font font = new Font(fontName, fontByteBuffer, fontPointer);
        font.setSize(IkGui.getFontSize());

        fonts.put(fontName, font);
        return true;
    }

    private StagedBitmap loadGlyph(@NonNull Font font, char c, @NonNull CacheElement element) {
        font.lock.lock();
        try {
            // Tabs and line breaks use the space glyph
            int glyphIndex = FT_Get_Char_Index(font.face, isSpecialWhitespace(c) ? ' ' : c);
            int error = FT_Load_Glyph(font.face, glyphIndex, FT_LOAD_DEFAULT);
            if (error != FT_Err_Ok) {
                IkGuiInternal.reportError(log, "Failed to load char {} for font {}", c, font.name);
                return null;
            }
            FT_GlyphSlot slot = Objects.requireNonNull(font.face.glyph());
            FT_Bitmap bitmap = slot.bitmap();

            // In 26.6 pixel format (i.e. 1/64 of a pixel). Glyphs without a bitmap like spaces
            // still have an advance.
            element.info.advance = slot.advance().x() / 64.0f;
            if (c == '\t') {
                element.info.advance *= TAB_SIZE;
            } else if (c == '\n' || c == '\r') {
                element.info.advance = 0.0f;
            }

            if (slot.format() != FT_GLYPH_FORMAT_BITMAP) {
                error = FT_Render_Glyph(slot, FT_RENDER_MODE_NORMAL);
                if (error != FT_Err_Ok) {
                    IkGuiInternal.reportError(
                            log, "Failed to render char {} for font {}", c, font.name);
                    return null;
                }
            }

            final int width = bitmap.width();
            final int height = bitmap.rows();
            final int totalPixels = width * height;
            int originalBufferSize = Math.abs(bitmap.pitch()) * height;

            ByteBuffer oldContents = bitmap.buffer(originalBufferSize);
            if (oldContents == null || width == 0 || height == 0) {
                element.info.width = 0;
                element.info.height = 0;
                return EMPTY_BITMAP;
            }
            ByteBuffer newContents = ByteBuffer.allocateDirect(width * height * Integer.BYTES);

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
                        newContents.putInt(currentByte);
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
                    IkGuiInternal.reportError(
                            log,
                            "Unexpected pixel mode {} for font {}",
                            bitmap.pixel_mode(),
                            font.name);
                    return null;
            }
            newContents.flip();

            FT_Glyph_Metrics metrics = slot.metrics();

            if (!allocAtlasSpace(width, height, element)) {
                return null;
            }
            element.info.width = width;
            element.info.height = height;
            // TODO(ches) handle vertical font bearings
            // These are in 26.6 pixel format (i.e. 1/64 of a pixel). Hence the dividing.
            element.info.bearingX = (int) (metrics.horiBearingX() / 64);
            element.info.bearingY = (int) (metrics.horiBearingY() / 64);

            StagedBitmap newBitmap =
                    new StagedBitmap(newContents, element.info.x, element.info.y, width, height);
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

    /**
     * Load a character in the font map, deducing the font based on which font supports it. We start
     * looking at the current font, then font fallbacks in order if not supported. If a font is not
     * found, we will use the fallback character.
     *
     * @param c The character to load.
     * @param fontSize The font size.
     */
    @Synchronized
    public void registerCharacter(char c, int fontSize) {
        final ResolvedChar resolved = resolveFont(c);
        if (resolved == null) {
            IkGuiInternal.reportError(log, "No fonts are loaded to display the character '{}'", c);
            return;
        }
        if (resolved.value() != c) {
            IkGuiInternal.reportError(log, "No loaded font supports the character '{}'", c);
        }
        cacheAdd(resolved.font(), resolved.value(), fontSize);
    }

    /**
     * Fetch the names of the loaded fonts, for debugging.
     *
     * @return The font names, sorted.
     */
    @Synchronized
    public List<String> getFontNames() {
        final List<String> names = new ArrayList<>(fonts.keySet());
        Collections.sort(names);
        return names;
    }

    /**
     * Fetch the characters currently in the cache, most recently used first, for debugging.
     *
     * @param maxCount The maximum number of characters to return.
     * @return Copies of the cached character info.
     */
    @Synchronized
    public List<CharInfo> getCachedCharacters(int maxCount) {
        final List<CharInfo> result = new ArrayList<>();
        for (CacheElement element = cacheHead.next;
                element != null && element != cacheTail && result.size() < maxCount;
                element = element.next) {
            final CharInfo copy = new CharInfo(element.info.value);
            copy.x = element.info.x;
            copy.y = element.info.y;
            copy.width = element.info.width;
            copy.height = element.info.height;
            copy.bearingX = element.info.bearingX;
            copy.bearingY = element.info.bearingY;
            copy.advance = element.info.advance;
            result.add(copy);
        }
        return result;
    }

    /**
     * Count the characters currently in the cache, for debugging.
     *
     * @return The number of cached characters.
     */
    @Synchronized
    public int getCachedCharacterCount() {
        int count = 0;
        for (CacheElement element = cacheHead.next;
                element != null && element != cacheTail;
                element = element.next) {
            ++count;
        }
        return count;
    }

    /**
     * Fetch the number of shelves (horizontal strips) allocated in the texture, for debugging.
     *
     * @return The shelf count.
     */
    @Synchronized
    public int getShelfCount() {
        return shelves.size();
    }

    /**
     * Fetch the total height of the texture used by shelves, for debugging.
     *
     * @return The used height, in pixels.
     */
    @Synchronized
    public int getShelvesHeight() {
        int height = 0;
        for (Shelf shelf : shelves) {
            height += shelf.height;
        }
        return height;
    }

    /**
     * Unload a font, cleaning up its resources. This will pull the font out of the context structs
     * if it's in use.
     *
     * @param fontPath The path to the font in the plugin data folder.
     */
    @Synchronized
    public void unloadFont(@NonNull String fontPath) {
        Font font = fonts.get(fontPath);
        if (font == null) {
            log.warn("Font {} not found when trying to destroy it in the font atlas", fontPath);
            return;
        }
        // Free up the space its glyphs were using
        for (CacheElement element = cacheHead.next; element != cacheTail; ) {
            final CacheElement next = element.next;
            if (element.font == font) {
                evict(element);
            }
            element = next;
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
