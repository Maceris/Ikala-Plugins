package com.ikalagaming.graphics.frontend.gui.data;

import static org.lwjgl.util.freetype.FreeType.*;

import com.ikalagaming.graphics.frontend.gui.IkGui;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_Size_Request;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class Font {
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

    /** Whether the font face contains kerning information. */
    final boolean supportsKerning;

    /** The current font size we are dealing with. */
    int size;

    /** Temporary storage for size info. */
    FT_Size_Request sizeRequest;

    public Font(
            @NonNull String name,
            @NonNull ByteBuffer fontData,
            @NonNull PointerBuffer freeTypeFont) {
        this.name = name;
        this.fontData = fontData;
        this.freeTypeFont = freeTypeFont;
        face = FT_Face.create(freeTypeFont.get(0));
        this.lock = new ReentrantLock();
        size = 0;
        sizeRequest = FT_Size_Request.create();
        supportsKerning = (face.face_flags() & FT_FACE_FLAG_KERNING) != 0;
    }

    /**
     * Set the font size, for rendering and kerning purposes. It's expected that we only deal with
     * one font size at a time for each font.
     *
     * @param fontSize The new font size.
     */
    public void setSize(int fontSize) {
        if (size == fontSize || fontSize < 0) {
            return;
        }

        this.lock.lock();
        try {
            int dpiFont = IkGui.getContext().dpiScaleFont;
            int dpiScreen = IkGui.getContext().dpiScaleScreen;

            sizeRequest.set(
                    FT_SIZE_REQUEST_TYPE_NOMINAL,
                    0,
                    (long) (fontSize * (dpiFont / 72f) * 64),
                    dpiScreen,
                    dpiScreen);
            int error = FT_Request_Size(face, sizeRequest);
            if (error != FT_Err_Ok) {
                log.error(
                        "Failed to request size {} for font {}: {} ({})",
                        fontSize,
                        name,
                        FT_Error_String(error),
                        error);
                return;
            }

            size = fontSize;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Check if the font supports the specified character.
     *
     * @param c The character to look for.
     * @return If we have a glyph for the given character.
     */
    public boolean supports(char c) {
        int glyphIndex = FT_Get_Char_Index(face, c);
        return glyphIndex != 0;
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
            sizeRequest.free();
            sizeRequest = null;
        }
    }
}
