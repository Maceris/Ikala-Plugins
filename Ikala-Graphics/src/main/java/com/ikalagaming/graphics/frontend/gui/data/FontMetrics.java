package com.ikalagaming.graphics.frontend.gui.data;

/**
 * The vertical metrics of a font at a specific size, in pixels. These come from the font face, so
 * they are the same for every glyph and string.
 *
 * <p>The font's ascent and descent are often larger than the line height, as fonts leave room for
 * accents and tall scripts. So text is laid out with the baseline at {@link
 * #baselineOffset(float)}, which keeps descenders inside the line.
 *
 * @param ascent The distance from the baseline to the top of the tallest glyphs, positive.
 * @param descent The distance from the baseline to the bottom of the lowest glyphs, which is
 *     negative since it's below the baseline.
 * @param lineGap The recommended extra space between lines, on top of ascent - descent.
 */
public record FontMetrics(float ascent, float descent, float lineGap) {
    /**
     * The distance from the top of a line to the baseline, given the height of the line. Puts the
     * baseline so that the descent of the font reaches the bottom of the line.
     *
     * @param lineHeight The height of a line of text.
     * @return The distance from the top of the line to the baseline.
     */
    public float baselineOffset(float lineHeight) {
        return lineHeight + descent;
    }
}
