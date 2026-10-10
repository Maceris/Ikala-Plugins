package com.ikalagaming.graphics.gui.data;

import static org.lwjgl.util.freetype.FreeType.*;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.IkGuiInternal;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_Size;
import org.lwjgl.util.freetype.FT_Size_Metrics;
import org.lwjgl.util.freetype.FT_Size_Request;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * The vertical metrics we have already calculated, keyed by {@link #metricsKey(int, int, int)}.
     * Guarded by the lock.
     */
    private final Map<Long, FontMetrics> metrics = new HashMap<>();

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

            // Like Dear ImGui, the font size is the height of a line: the font's ascent plus its
            // descent. A nominal (em) size would make the glyphs taller than the line, which
            // pushes text up in its line and clips the bottoms of letters.
            sizeRequest.set(
                    FT_SIZE_REQUEST_TYPE_REAL_DIM,
                    0,
                    (long) (fontSize * (dpiFont / 72f) * 64),
                    dpiScreen,
                    dpiScreen);
            int error = FT_Request_Size(face, sizeRequest);
            if (error != FT_Err_Ok) {
                IkGuiInternal.reportError(
                        log,
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
     * Fetch the vertical metrics of the font at a size, for the current DPI settings. These are
     * cached, so only the first call for a size has to ask FreeType.
     *
     * @param fontSize The font size.
     * @return The metrics, in pixels.
     */
    public FontMetrics getMetrics(int fontSize) {
        final int dpiFont = IkGui.getContext().dpiScaleFont;
        final int dpiScreen = IkGui.getContext().dpiScaleScreen;
        final long key = metricsKey(fontSize, dpiFont, dpiScreen);
        this.lock.lock();
        try {
            FontMetrics result = metrics.get(key);
            if (result != null) {
                return result;
            }
            // The size is skipped if it hasn't changed, which would miss DPI changes
            size = 0;
            setSize(fontSize);
            final FT_Size faceSize = face.size();
            if (faceSize == null) {
                IkGuiInternal.reportError(log, "Font {} has no size to read metrics from", name);
                return new FontMetrics(0, 0, 0);
            }
            final FT_Size_Metrics sizeMetrics = faceSize.metrics();
            // These are in 26.6 pixel format (i.e. 1/64 of a pixel). Hence the dividing.
            final float ascent = sizeMetrics.ascender() / 64.0f;
            final float descent = sizeMetrics.descender() / 64.0f;
            final float height = sizeMetrics.height() / 64.0f;
            result = new FontMetrics(ascent, descent, Math.max(0.0f, height - (ascent - descent)));
            metrics.put(key, result);
            return result;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Combine the things that affect the metrics into a single key.
     *
     * @param fontSize The font size.
     * @param dpiFont The DPI that font sizes are specified in.
     * @param dpiScreen The DPI of the screen.
     * @return The key.
     */
    private static long metricsKey(int fontSize, int dpiFont, int dpiScreen) {
        return ((long) fontSize << 32) | ((long) (dpiFont & 0xFFFF) << 16) | (dpiScreen & 0xFFFF);
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
                IkGuiInternal.reportError(
                        log,
                        "Failed to destroy face for font {}: {}",
                        name,
                        FT_Error_String(error));
            }
            freeTypeFont = null;
            fontData = null;
            // Allocated with create(), so the garbage collector frees it, calling free() would
            // corrupt the heap
            sizeRequest = null;
        }
    }
}
