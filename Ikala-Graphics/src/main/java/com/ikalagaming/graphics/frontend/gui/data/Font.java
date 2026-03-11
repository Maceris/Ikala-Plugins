package com.ikalagaming.graphics.frontend.gui.data;

import static org.lwjgl.util.freetype.FreeType.*;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.freetype.FT_Face;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class Font {
    // TODO(ches) font

    /** The name of the font, which is the path to the font from the plugins data directory. */
    public final String name;

    ByteBuffer fontData;
    PointerBuffer freeTypeFont;
    FT_Face face;

    /**
     * We don't want to be messing with the face from multiple threads at the same time. So this
     * must be used to synchronize access to mess around with the font internals, for loading glyphs
     * and similar.
     */
    ReentrantLock lock;

    public Font(
            @NonNull String name,
            @NonNull ByteBuffer fontData,
            @NonNull PointerBuffer freeTypeFont) {
        this.name = name;
        this.fontData = fontData;
        this.freeTypeFont = freeTypeFont;
        face = FT_Face.create(freeTypeFont.get(0));
        this.lock = new ReentrantLock();
    }

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
