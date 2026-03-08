package com.ikalagaming.graphics.frontend.gui.data;

import java.util.ArrayList;
import java.util.List;

public class FontAtlas {
    // TODO(ches) font

    /** Maximum height of a font atlas image, in pixels. */
    public static final int MAX_FONT_ATLAS_IMAGE_HEIGHT = 1024;

    /** Maximum width of a font atlas image, in pixels. */
    public static final int MAX_FONT_ATLAS_IMAGE_WIDTH = 1024;

    public static final int INVALID_INDEX = -1;
    private final List<FontPage> pages;

    public static int charactersPerPage(int size) {
        int squarePixels = MAX_FONT_ATLAS_IMAGE_HEIGHT * MAX_FONT_ATLAS_IMAGE_WIDTH;
        int roughEstimate = Font.estimatedHeight(size);
        roughEstimate *= roughEstimate;

        return squarePixels / roughEstimate;
    }

    public FontAtlas() {
        pages = new ArrayList<>();
    }

    public int getFontMapIndex(char c, int size) {
        return INVALID_INDEX;
    }

    public void createDefaultPage(int fontSize) {
        boolean ranOutOfSpace;
        char nextCharacter = ' ';

        do {
            FontPage page = new FontPage(fontSize);
            pages.add(page);
            ranOutOfSpace = false;

            // Add all the printable ascii characters
            for (char c = nextCharacter; c <= '~'; ++c) {
                if (!page.addCharacter(c)) {
                    ranOutOfSpace = true;
                    nextCharacter = c;
                    break;
                }
            }
        } while (!ranOutOfSpace);
        // TODO(ches) add extended latin characters, bake
    }
}
