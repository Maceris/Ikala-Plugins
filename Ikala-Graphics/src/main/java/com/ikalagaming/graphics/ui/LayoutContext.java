package com.ikalagaming.graphics.ui;

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
 */
public record LayoutContext(
        float scale,
        float fontSize,
        float framePaddingX,
        float framePaddingY,
        @NonNull TextMeasurer text) {

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
