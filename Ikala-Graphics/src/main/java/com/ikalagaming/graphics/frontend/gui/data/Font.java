package com.ikalagaming.graphics.frontend.gui.data;

public class Font {
    // TODO(ches) font

    /**
     * Convert the height of a letter from pt (points) to px (pixels).
     *
     * @param pt The size in points.
     * @return The height in pixels.
     */
    public static int estimatedHeight(int pt) {
        float pixels = pt * 1.333f;
        return (int) pixels;
    }
}
