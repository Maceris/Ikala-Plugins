package com.ikalagaming.graphics.frontend.gui.data;

import org.joml.Vector2i;

public class FontAtlas {
    // TODO(ches) font

    /** Maximum height of a font atlas image, in pixels. */
    public static final int MAX_FONT_ATLAS_IMAGE_HEIGHT = 2048;

    /** Maximum width of a font atlas image, in pixels. */
    public static final int MAX_FONT_ATLAS_IMAGE_WIDTH = 2048;

    public static final int INVALID_INDEX = -1;

    private FontCache fontCache;

    public static int charactersPerPage(int size) {
        int squarePixels = MAX_FONT_ATLAS_IMAGE_HEIGHT * MAX_FONT_ATLAS_IMAGE_WIDTH;
        int roughEstimate = Font.estimatedHeight(size);
        roughEstimate *= roughEstimate;

        return squarePixels / roughEstimate;
    }

    public FontAtlas() {
        fontCache = new FontCache();
    }

    public void getFontMapPosition(char c, int fontSize, Vector2i output) {
        // TODO(ches) look up the position
    }

    public void registerCharacter(char c, int fontSize) {
        fontCache.add(c, fontSize);
    }

    public void addDefaultCharacters(int fontSize) {
        // Add all the printable ascii characters
        for (char c = ' '; c <= '~'; ++c) {
            registerCharacter(c, fontSize);
        }
        // TODO(ches) add extended latin characters
    }
}
