package com.ikalagaming.graphics.frontend.gui.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ikalagaming.graphics.frontend.gui.IkGui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Tests for the glyph cache and texture allocation of the font atlas. */
class FontAtlasTest {
    private Context context;

    /** The atlas being tested, which replaces the context's atlas. */
    private FontAtlas atlas;

    private byte[] fontData;

    @BeforeEach
    void setUp() throws IOException {
        context = IkGui.createContext();
        context.io.iniFilename = null;
        try (InputStream stream = getClass().getResourceAsStream("/fonts/NotoSans.ttf")) {
            assertNotNull(stream, "Missing test font");
            fontData = stream.readAllBytes();
        }
    }

    @AfterEach
    void tearDown() {
        if (atlas != null && atlas != context.io.fonts) {
            atlas.destroy();
        }
        IkGui.destroyContext();
    }

    /**
     * Use a new atlas with the given lookup table size, with the test font loaded under each of the
     * given names. The first is made the current font.
     */
    private void useAtlas(int cacheSize, String... fontNames) {
        atlas = new FontAtlas(cacheSize);
        for (String name : fontNames) {
            assertTrue(atlas.loadFont(name, fontData));
        }
        context.font = atlas.getFont(fontNames[0]);
        context.fontFallbacks.clear();
    }

    private FontAtlas.CharInfo info(char c, int fontSize) {
        return atlas.getFontMapInfo(c, fontSize).orElseThrow();
    }

    private boolean anyErrors() {
        return context.debugLogBuffer.snapshot().stream()
                .anyMatch(line -> line.contains("[ikgui-error]"));
    }

    /** Check that no cached glyphs overlap each other or leave the texture. */
    private void assertNoOverlaps() {
        final List<FontAtlas.CharInfo> glyphs = new ArrayList<>();
        for (FontAtlas.CharInfo info : atlas.getCachedCharacters(Integer.MAX_VALUE)) {
            if (info.width > 0 && info.height > 0) {
                glyphs.add(info);
            }
        }
        for (int i = 0; i < glyphs.size(); ++i) {
            final FontAtlas.CharInfo a = glyphs.get(i);
            assertTrue(a.x >= 0 && a.x + a.width <= FontAtlas.FONT_ATLAS_IMAGE_WIDTH);
            assertTrue(a.y >= 0 && a.y + a.height <= FontAtlas.FONT_ATLAS_IMAGE_HEIGHT);
            for (int j = i + 1; j < glyphs.size(); ++j) {
                final FontAtlas.CharInfo b = glyphs.get(j);
                final boolean overlap =
                        a.x < b.x + b.width
                                && b.x < a.x + a.width
                                && a.y < b.y + b.height
                                && b.y < a.y + a.height;
                assertFalse(overlap, "'" + a.value + "' overlaps '" + b.value + "'");
            }
        }
    }

    @Test
    void testSameCharacterInDifferentSizesCollides() {
        // A single slot, so every glyph shares a chain
        useAtlas(1, "Noto");
        atlas.registerCharacter('x', 16);
        atlas.registerCharacter('x', 48);
        final FontAtlas.CharInfo small = info('x', 16);
        final FontAtlas.CharInfo large = info('x', 48);
        assertNotEquals(small.height, large.height, "Each size needs its own glyph");
        assertTrue(large.height > small.height * 2);
        assertEquals(2, atlas.getCachedCharacterCount());
    }

    @Test
    void testSameCharacterInDifferentFontsCollides() {
        useAtlas(1, "First", "Second");
        atlas.registerCharacter('x', 16);
        final FontAtlas.CharInfo first = info('x', 16);
        // The same glyph isn't reused for another font, even though the character and size match
        context.font = atlas.getFont("Second");
        assertTrue(atlas.getFontMapInfo('x', 16).isEmpty());
        atlas.registerCharacter('x', 16);
        final FontAtlas.CharInfo second = info('x', 16);
        assertTrue(first.x != second.x || first.y != second.y);
        assertEquals(2, atlas.getCachedCharacterCount());
    }

    @Test
    void testRegisteringTwiceKeepsOneGlyph() {
        useAtlas(1, "Noto");
        atlas.registerCharacter('a', 16);
        atlas.registerCharacter('b', 16);
        atlas.registerCharacter('a', 16);
        assertEquals(2, atlas.getCachedCharacterCount());
        // And it's moved to the front of the LRU list
        assertEquals('a', atlas.getCachedCharacters(1).getFirst().value);
    }

    @Test
    void testEvictionKeepsGlyphsSeparate() {
        useAtlas(1 << 14, "Noto");
        // Huge glyphs, so the texture fills up after a few dozen and older ones get evicted
        final int fontSize = 300;
        for (char c = '!'; c <= '~'; ++c) {
            atlas.registerCharacter(c, fontSize);
            // The newest glyph is always available
            assertTrue(atlas.getFontMapInfo(c, fontSize).isPresent(), "Missing '" + c + "'");
            assertNoOverlaps();
        }
        assertTrue(
                atlas.getCachedCharacterCount() < '~' - '!' + 1,
                "The test should have evicted some glyphs");
        assertTrue(atlas.getShelvesHeight() <= FontAtlas.FONT_ATLAS_IMAGE_HEIGHT);
        assertFalse(anyErrors(), context.debugLogBuffer.getText());

        // Smaller glyphs can still be added afterward
        for (char c = 'a'; c <= 'z'; ++c) {
            atlas.registerCharacter(c, 16);
        }
        assertNoOverlaps();
        assertFalse(anyErrors(), context.debugLogBuffer.getText());
    }

    @Test
    void testEvictingSpacesDoesNotFreeSpace() {
        useAtlas(1 << 14, "Noto");
        // Spaces have no bitmap, and will be the oldest elements when evicting
        atlas.registerCharacter(' ', 16);
        atlas.registerCharacter(' ', 300);
        for (char c = '!'; c <= '~'; ++c) {
            atlas.registerCharacter(c, 300);
        }
        assertNoOverlaps();
        assertFalse(anyErrors(), context.debugLogBuffer.getText());
    }

    @Test
    void testUnloadingAFontDropsItsGlyphs() {
        useAtlas(1 << 14, "First", "Second");
        for (char c = 'a'; c <= 'z'; ++c) {
            atlas.registerCharacter(c, 16);
        }
        context.font = atlas.getFont("Second");
        for (char c = 'a'; c <= 'e'; ++c) {
            atlas.registerCharacter(c, 16);
        }
        assertEquals(26 + 5, atlas.getCachedCharacterCount());
        atlas.unloadFont("First");
        assertEquals(5, atlas.getCachedCharacterCount());
        assertTrue(atlas.getFontMapInfo('a', 16).isPresent());
        assertFalse(anyErrors(), context.debugLogBuffer.getText());
    }

    @Test
    void testUnsupportedCharacterWithoutFallbacks() {
        useAtlas(1 << 14, "Noto");
        // No fallback fonts, so the fallback character comes from the current font
        assertDoesNotThrow(() -> atlas.registerCharacter('一', 16));
        assertEquals(context.fontFallbackChar, info('一', 16).value);
    }

    @Test
    void testNoFonts() {
        useAtlas(1 << 14, "Noto");
        context.font = null;
        assertDoesNotThrow(() -> atlas.registerCharacter('a', 16));
        assertTrue(atlas.getFontMapInfo('a', 16).isEmpty());
    }

    @Test
    void testDestroyContextDestroysAtlas() {
        final FontAtlas owned = context.io.fonts;
        assertFalse(owned.isDestroyed());
        IkGui.destroyContext();
        assertTrue(owned.isDestroyed());
        // So the tear down has a context to destroy
        context = IkGui.createContext();
    }

    @Test
    void testAdvances() {
        useAtlas(1 << 14, "Noto");
        atlas.registerCharacter('a', 16);
        atlas.registerCharacter(' ', 16);
        atlas.registerCharacter('W', 16);
        final FontAtlas.CharInfo a = info('a', 16);
        final FontAtlas.CharInfo space = info(' ', 16);
        final FontAtlas.CharInfo w = info('W', 16);
        assertTrue(a.advance > 0);
        // Spaces have nothing to draw, but still move the pen
        assertEquals(0, space.width);
        assertTrue(space.advance > 0);
        assertTrue(w.advance > a.advance);
        // The advance includes the space on both sides of the glyph
        assertTrue(a.advance >= a.width);
    }

    @Test
    void testNoKerningWithoutKerningTable() {
        // NotoSans only has GPOS kerning, which FreeType doesn't apply
        useAtlas(1 << 14, "Noto", "Other");
        assertEquals(0.0f, atlas.getKerning('A', 'V', 16));
        // Characters from different fonts are never kerned
        context.fontFallbacks.add(atlas.getFont("Other"));
        assertEquals(0.0f, atlas.getKerning('A', '\u4e00', 16));
    }

    @Test
    void testKerningIsScaledPixels() throws IOException {
        // Arial has a kerning table, but isn't available everywhere
        final Path arial = Path.of("C:/Windows/Fonts/arial.ttf");
        assumeTrue(Files.isReadable(arial), "Arial is not installed");
        atlas = new FontAtlas();
        assertTrue(atlas.loadFont("Arial", Files.readAllBytes(arial)));
        context.font = atlas.getFont("Arial");
        context.fontFallbacks.clear();

        final float small = atlas.getKerning('A', 'V', 16);
        final float large = atlas.getKerning('A', 'V', 64);
        // A and V are pulled together
        assertTrue(small < 0, "Expected negative kerning, got " + small);
        // In pixels at the size, much smaller than the glyphs, rather than font units
        assertTrue(small > -16 * 0.5f, "Kerning is too large: " + small);
        assertEquals(small * 4, large, 4.0f);
        // Grid-fitted to whole pixels
        assertEquals(Math.round(small), small);
        // Pairs without kerning
        assertEquals(0.0f, atlas.getKerning('o', 'o', 16));
    }

    private static List<FontAtlas.FreeBlock> blocks(int... positionsAndSizes) {
        final List<FontAtlas.FreeBlock> list = new ArrayList<>();
        for (int i = 0; i < positionsAndSizes.length; i += 2) {
            list.add(new FontAtlas.FreeBlock(positionsAndSizes[i], positionsAndSizes[i + 1]));
        }
        return list;
    }

    private static void assertBlocks(List<FontAtlas.FreeBlock> list, int... positionsAndSizes) {
        assertEquals(positionsAndSizes.length / 2, list.size());
        for (int i = 0; i < list.size(); ++i) {
            assertEquals(positionsAndSizes[i * 2], list.get(i).position, "position of " + i);
            assertEquals(positionsAndSizes[i * 2 + 1], list.get(i).size, "size of " + i);
        }
    }

    @Test
    void testMergeBlock() {
        // Right before a block
        List<FontAtlas.FreeBlock> list = blocks(10, 5);
        FontAtlas.mergeBlock(list, new FontAtlas.FreeBlock(7, 3));
        assertBlocks(list, 7, 8);

        // Right after a block
        list = blocks(10, 5);
        FontAtlas.mergeBlock(list, new FontAtlas.FreeBlock(15, 5));
        assertBlocks(list, 10, 10);

        // Filling the gap between two blocks
        list = blocks(0, 5, 10, 5);
        FontAtlas.mergeBlock(list, new FontAtlas.FreeBlock(5, 5));
        assertBlocks(list, 0, 15);

        // Disconnected blocks stay sorted
        list = blocks(0, 2, 10, 2, 20, 2);
        FontAtlas.mergeBlock(list, new FontAtlas.FreeBlock(15, 2));
        FontAtlas.mergeBlock(list, new FontAtlas.FreeBlock(5, 2));
        FontAtlas.mergeBlock(list, new FontAtlas.FreeBlock(30, 2));
        assertBlocks(list, 0, 2, 5, 2, 10, 2, 15, 2, 20, 2, 30, 2);

        // Empty blocks are ignored
        list = blocks(0, 2);
        FontAtlas.mergeBlock(list, new FontAtlas.FreeBlock(5, 0));
        assertBlocks(list, 0, 2);
    }
}
