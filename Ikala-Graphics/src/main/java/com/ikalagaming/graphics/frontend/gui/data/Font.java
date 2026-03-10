package com.ikalagaming.graphics.frontend.gui.data;

import static org.lwjgl.util.freetype.FreeType.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;

import java.nio.ByteBuffer;

@Slf4j
@RequiredArgsConstructor
public class Font {
    // TODO(ches) font

    /** The name of the font, which is the path to the font from the plugins data directory. */
    public final String name;

    ByteBuffer fontData;
    PointerBuffer freeTypeFont;

    /**
     * Free up resources for the font. This must be done when we are done with the font, or it will
     * leak resources.
     */
    void destroy() {
        if (freeTypeFont != null) {
            int error = nFT_Done_Face(freeTypeFont.get(0));
            if (error != FT_Err_Ok) {
                log.error("Failed to create memory face: {}", FT_Error_String(error));
            }
            freeTypeFont = null;
            fontData = null;
        }
    }

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
