package com.ikalagaming.graphics.ui;

import com.ikalagaming.graphics.ui.style.ActiveTheme;

import lombok.NonNull;

/**
 * What the layout engine needs to know about the environment.
 *
 * @param scale Pixels per UI unit: the screen's content scale times the user's UI scale.
 * @param fontSize The default font size in UI units, used where no node sets one. Font sizes are
 *     what IkGui pushes fonts with, which is smaller than the line height.
 * @param framePaddingX IkGui's frame padding around widget text, horizontally, in pixels.
 * @param framePaddingY IkGui's frame padding around widget text, vertically, in pixels.
 * @param text Measures text.
 * @param theme The theme nodes are styled with.
 */
public record LayoutContext(
        float scale,
        float fontSize,
        float framePaddingX,
        float framePaddingY,
        @NonNull TextMeasurer text,
        @NonNull ActiveTheme theme) {

    /**
     * Create a layout context without a theme, so IkGui's own style is used.
     *
     * @param scale Pixels per UI unit.
     * @param fontSize The default font size in UI units.
     * @param framePaddingX IkGui's frame padding around widget text, horizontally, in pixels.
     * @param framePaddingY IkGui's frame padding around widget text, vertically, in pixels.
     * @param text Measures text.
     */
    public LayoutContext(
            float scale,
            float fontSize,
            float framePaddingX,
            float framePaddingY,
            @NonNull TextMeasurer text) {
        this(scale, fontSize, framePaddingX, framePaddingY, text, ActiveTheme.EMPTY);
    }

    /**
     * IkGui's frame padding as insets, for widgets the theme doesn't pad.
     *
     * @return The frame padding, in UI units.
     */
    public Insets framePadding() {
        return Insets.symmetric(Length.u(framePaddingX / scale), Length.u(framePaddingY / scale));
    }

    /** Measures how much space text takes. */
    @FunctionalInterface
    public interface TextMeasurer {
        /**
         * Measure a piece of text on one line.
         *
         * @param text The text.
         * @param fontPixels The font size in pixels.
         * @param out Receives the width at 0 and the height at 1, in pixels.
         */
        void measure(@NonNull String text, float fontPixels, float[] out);
    }
}
