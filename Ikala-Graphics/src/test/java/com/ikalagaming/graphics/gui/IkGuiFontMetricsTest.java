package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DrawData;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.FontAtlas;
import com.ikalagaming.graphics.gui.data.FontMetrics;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Color;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Tests for the vertical font metrics, and the text baseline that uses them. */
class IkGuiFontMetricsTest {
    /**
     * NotoSans units per em, and its ascender and descender in those units, from the hhea table.
     */
    private static final float UNITS_PER_EM = 1000.0f;

    private static final float ASCENDER = 1069.0f;
    private static final float DESCENDER = -293.0f;

    private static final int FONT_SIZE = 16;

    private Context context;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    /** Load the test font from the test resources, and make it the current font. */
    private void loadTestFont() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/fonts/NotoSans.ttf")) {
            assertNotNull(stream, "Missing test font");
            assertTrue(context.io.fonts.loadFont("NotoSans", stream.readAllBytes()));
        }
        IkGui.setFont("NotoSans", FONT_SIZE);
    }

    /** The height of a line in pixels, for a font size. */
    private float linePixels(int fontSize) {
        return fontSize * (float) context.dpiScaleScreen / context.dpiScaleFont;
    }

    /**
     * The size of an em in pixels, for a font size. The font size is the line height, which is the
     * font's ascent plus its descent, so the em is smaller.
     */
    private float emPixels(int fontSize) {
        return linePixels(fontSize) * UNITS_PER_EM / (ASCENDER - DESCENDER);
    }

    @Test
    void testNoFontHasNoMetrics() {
        assertNull(IkGuiInternal.getFontMetrics());
        assertNull(context.io.fonts.getFontMetrics("Missing", FONT_SIZE));
    }

    @Test
    void testMetricsMatchTheFont() throws IOException {
        loadTestFont();
        final FontMetrics metrics = IkGuiInternal.getFontMetrics();
        assertNotNull(metrics);
        final float em = emPixels(FONT_SIZE);
        // FreeType rounds the scaled values to whole pixels
        assertEquals(ASCENDER / UNITS_PER_EM * em, metrics.ascent(), 1.0f);
        assertEquals(DESCENDER / UNITS_PER_EM * em, metrics.descent(), 1.0f);
        assertTrue(metrics.lineGap() >= 0.0f);
        assertSame(metrics, context.io.fonts.getFontMetrics("NotoSans", FONT_SIZE));
    }

    @Test
    void testMetricsArePerSize() throws IOException {
        loadTestFont();
        final FontAtlas atlas = context.io.fonts;
        final FontMetrics small = atlas.getFontMetrics(FONT_SIZE);
        final FontMetrics large = atlas.getFontMetrics(FONT_SIZE * 2);
        // Cached, so the same size gives the same instance
        assertSame(small, atlas.getFontMetrics(FONT_SIZE));
        assertEquals(small.ascent() * 2, large.ascent(), 2.0f);
        assertEquals(small.descent() * 2, large.descent(), 2.0f);
    }

    @Test
    void testFallbackFontMetrics() throws IOException {
        loadTestFont();
        context.font = null;
        context.fontFallbacks.clear();
        assertNull(context.io.fonts.getFontMetrics(FONT_SIZE));
        IkGui.setFontFallbacks("NotoSans");
        assertSame(
                context.io.fonts.getFontMetrics("NotoSans", FONT_SIZE),
                context.io.fonts.getFontMetrics(FONT_SIZE));
    }

    /**
     * Draw text, and find the baseline of each glyph from its quad and bearing.
     *
     * @param text The text, without spaces so that every character has a quad.
     * @param posY The top of the text.
     * @return The baseline of every glyph.
     */
    private List<Float> glyphBaselines(String text, float posY) {
        final DrawList drawList = new DrawList("Test");
        drawList.addText(FONT_SIZE, 10, posY, Color.WHITE, text);
        drawList.prepareForRender();

        final List<Float> baselines = new ArrayList<>();
        final int count = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        int glyph = 0;
        for (int i = 0; i < count; ++i) {
            final int offset = i * DrawData.SIZE_OF_DRAW_COMMAND;
            if (drawList.commandBuffer.getInt(offset + 16)
                    != DrawList.ElementType.TEXT.getTypeID()) {
                continue;
            }
            final int point = drawList.commandBuffer.getInt(offset) * DrawData.SIZE_OF_POINT;
            final float quadMinY = drawList.pointBuffer.getFloat(point + 4);
            final FontAtlas.CharInfo info =
                    context.io.fonts.getFontMapInfo(text.charAt(glyph++), FONT_SIZE).orElseThrow();
            baselines.add(quadMinY + info.bearingY);
        }
        assertEquals(text.length(), baselines.size());
        return baselines;
    }

    @Test
    void testBaselineIsTheSameForAnyText() throws IOException {
        loadTestFont();
        final FontMetrics metrics = IkGuiInternal.getFontMetrics();
        final float lineHeight = IkGuiInternal.getFontSize();
        final float expected = 100 + (float) Math.floor(metrics.baselineOffset(lineHeight));

        final List<Float> baselines = new ArrayList<>();
        // Short letters only, capitals and descenders, and accents
        baselines.addAll(glyphBaselines("ace", 100));
        baselines.addAll(glyphBaselines("Abc", 100));
        baselines.addAll(glyphBaselines("gjpqy", 100));
        baselines.addAll(glyphBaselines("ÁÉÍ", 100));
        for (float baseline : baselines) {
            assertEquals(expected, baseline, 0.001f);
        }
        // Descenders stay inside the line
        assertTrue(expected - metrics.descent() <= 100 + lineHeight + 1.0f);
    }

    /**
     * The top and bottom of every glyph's quad.
     *
     * @param text The text, without spaces so that every character has a quad.
     * @param posY The top of the text.
     * @return The smallest top and the largest bottom.
     */
    private float[] glyphExtent(String text, float posY) {
        final DrawList drawList = new DrawList("Test");
        drawList.addText(FONT_SIZE, 10, posY, Color.WHITE, text);
        drawList.prepareForRender();

        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        final int count = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        int glyph = 0;
        for (int i = 0; i < count; ++i) {
            final int offset = i * DrawData.SIZE_OF_DRAW_COMMAND;
            if (drawList.commandBuffer.getInt(offset + 16)
                    != DrawList.ElementType.TEXT.getTypeID()) {
                continue;
            }
            final int point = drawList.commandBuffer.getInt(offset) * DrawData.SIZE_OF_POINT;
            final float quadMinY = drawList.pointBuffer.getFloat(point + 4);
            final FontAtlas.CharInfo info =
                    context.io.fonts.getFontMapInfo(text.charAt(glyph++), FONT_SIZE).orElseThrow();
            top = Math.min(top, quadMinY);
            bottom = Math.max(bottom, quadMinY + info.height);
        }
        return new float[] {top, bottom};
    }

    @Test
    void testGlyphsFitInTheLine() throws IOException {
        loadTestFont();
        final float lineHeight = IkGuiInternal.getFontSize();
        // Capitals with accents reach the highest, and descenders the lowest
        final float[] extent = glyphExtent("ÁÉÍgjpqy", 100);
        assertTrue(extent[0] >= 100, "Glyph tops are inside the line, was " + extent[0]);
        assertTrue(
                extent[1] <= 100 + lineHeight,
                "Glyph bottoms are inside the line, was " + extent[1]);
    }

    @Test
    void testCapitalsAreNearTheMiddleOfTheLine() throws IOException {
        loadTestFont();
        final float lineHeight = IkGuiInternal.getFontSize();
        // A capital without descenders or accents, whose middle should be close to the line's
        final float[] extent = glyphExtent("H", 100);
        final float middle = (extent[0] + extent[1]) / 2;
        // The descent sits below, so capitals are a little above the middle, but not by much
        assertEquals(100 + lineHeight / 2, middle, lineHeight * 0.2f);
    }

    @Test
    void testTextIsLaidOutByAdvance() throws IOException {
        loadTestFont();
        final String text = "Wa b";
        final DrawList drawList = new DrawList("Test");
        final int width = drawList.addText(FONT_SIZE, 10, 100, Color.WHITE, text);
        drawList.prepareForRender();

        final FontAtlas atlas = context.io.fonts;
        final FontAtlas.CharInfo[] infos = new FontAtlas.CharInfo[text.length()];
        float expectedWidth = 0;
        for (int i = 0; i < text.length(); ++i) {
            infos[i] = atlas.getFontMapInfo(text.charAt(i), FONT_SIZE).orElseThrow();
            expectedWidth += infos[i].advance;
        }
        // NotoSans has no kerning table, so the width is just the advances
        assertEquals((int) Math.ceil(expectedWidth), width);
        assertEquals(
                (float) Math.ceil(expectedWidth), DrawList.calcTextWidth(FONT_SIZE, text), 0.001f);

        // Each glyph starts at the pen position plus its bearing, and the space has no quad
        final List<Float> quadLefts = new ArrayList<>();
        final int count = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        for (int i = 0; i < count; ++i) {
            final int offset = i * DrawData.SIZE_OF_DRAW_COMMAND;
            if (drawList.commandBuffer.getInt(offset + 16)
                    == DrawList.ElementType.TEXT.getTypeID()) {
                final int point = drawList.commandBuffer.getInt(offset) * DrawData.SIZE_OF_POINT;
                quadLefts.add(drawList.pointBuffer.getFloat(point));
            }
        }
        assertEquals(3, quadLefts.size());
        float penX = 10;
        int glyph = 0;
        for (FontAtlas.CharInfo info : infos) {
            if (info.width > 0) {
                assertEquals(
                        (float) Math.floor(penX + 0.5f) + info.bearingX,
                        quadLefts.get(glyph++),
                        0.001f,
                        "Glyph '" + info.value + "'");
            }
            penX += info.advance;
        }

        // The cursor offsets match the pen positions
        final float[] offsets = DrawList.calcTextOffsets(FONT_SIZE, text, 0, text.length());
        float pen = 0;
        for (int i = 0; i < text.length(); ++i) {
            pen += infos[i].advance;
            assertEquals((float) Math.ceil(pen), offsets[i + 1], 0.001f);
        }
    }

    @Test
    void testFontAtlasShowsMetrics() throws IOException {
        loadTestFont();
        for (int i = 0; i < 2; ++i) {
            IkGui.newFrame();
            IkGui.setNextWindowPos(10, 10, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(600, 600, Condition.FIRST_USE_EVER);
            IkGui.begin("Fonts", null, WindowFlags.NONE);
            IkGui.showFontAtlas(context.io.fonts);
            IkGui.textLink("A link with descenders gjpqy");
            IkGui.end();
            IkGui.render();
        }
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]")),
                context.debugLogBuffer.getText());
    }
}
