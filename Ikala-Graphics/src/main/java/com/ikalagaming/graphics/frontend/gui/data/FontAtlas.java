package com.ikalagaming.graphics.frontend.gui.data;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.freetype.FreeType.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.launcher.PluginFolder;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2i;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles all the loaded fonts, and the texture used to cache and render characters.
 */
@Slf4j
public class FontAtlas {
    // TODO(ches) font

    /** Maximum height of a font atlas image, in pixels. */
    public static final int MAX_FONT_ATLAS_IMAGE_HEIGHT = 2048;

    /** Maximum width of a font atlas image, in pixels. */
    public static final int MAX_FONT_ATLAS_IMAGE_WIDTH = 2048;

    public static final int INVALID_INDEX = -1;

    private final FontCache fontCache;

    private PointerBuffer freeTypeLibrary;
    private final Map<String, Font> fonts;
    private Font defaultFont;

    public static int charactersPerPage(int size) {
        int squarePixels = MAX_FONT_ATLAS_IMAGE_HEIGHT * MAX_FONT_ATLAS_IMAGE_WIDTH;
        int roughEstimate = Font.estimatedHeight(size);
        roughEstimate *= roughEstimate;

        return squarePixels / roughEstimate;
    }

    public FontAtlas() {
        fontCache = new FontCache();
        fonts = new HashMap<>();
        freeTypeLibrary = PointerBuffer.allocateDirect(1);

        int error = FT_Init_FreeType(freeTypeLibrary);
        if (error != FT_Err_Ok) {
            throw new IllegalStateException(
                    "Failed to initialize FreeType: " + FT_Error_String(error));
        }

        try (MemoryStack stack = stackPush()) {
            IntBuffer major = stack.mallocInt(1);
            IntBuffer minor = stack.mallocInt(1);
            IntBuffer patch = stack.mallocInt(1);

            FT_Library_Version(freeTypeLibrary.get(0), major, minor, patch);
            log.debug("Loaded FreeType v{}.{}.{}", major.get(0), minor.get(0), patch.get(0));
        }
    }

    /**
     * Free up any resources owned by the font atlas. This must be done when the atlas is no longer
     * needed, or there will be resource leaks.
     */
    public void destroy() {
        if (freeTypeLibrary == null) {
            log.error("Trying to destroy Font Atlas twice.");
            return;
        }
        defaultFont = null;
        fonts.forEach((k, v) -> v.destroy());
        fonts.clear();

        FT_Done_FreeType(freeTypeLibrary.get(0));
        freeTypeLibrary = null;
    }

    /**
     * Unload a font, cleaning up its resources.
     *
     * @param fontPath The path to the font in the plugin data folder.
     */
    public void unloadFont(@NonNull String fontPath) {
        Font font = fonts.get(fontPath);
        if (font == null) {
            log.warn("Font {} not found when trying to destroy it in the font atlas", fontPath);
            return;
        }
        if (defaultFont != null && fontPath.equals(defaultFont.name)) {
            defaultFont = null;
        }
        fonts.remove(fontPath);
        font.destroy();
    }

    /**
     * Load a font. Should only be called once per font as subsequent attempts will be ignored,
     * unless it's unloaded.
     *
     * @param fontPath The path to the font in the plugin data folder. This is treated as the name
     *     of the font.
     * @return Whether the font is currently loaded successfully.
     */
    public boolean loadFont(@NonNull String fontPath) {
        if (fonts.containsKey(fontPath)) {
            log.warn("Font {} already loaded, trying to load twice.", fontPath);
            return true;
        }

        try {
            File fontFile =
                    PluginFolder.getResource(
                            GraphicsPlugin.PLUGIN_NAME, PluginFolder.ResourceType.DATA, fontPath);

            if (!fontFile.canRead()) {
                log.error("Cannot read font file from data folder: {}", fontPath);
                return false;
            }

            byte[] fontData = Files.readAllBytes(fontFile.toPath());

            ByteBuffer fontByteBuffer = ByteBuffer.allocateDirect(fontData.length);
            fontByteBuffer.put(fontData);
            fontByteBuffer.flip();

            PointerBuffer fontPointer = PointerBuffer.allocateDirect(1);

            int error =
                    FT_New_Memory_Face(
                            freeTypeLibrary.get(0),
                            fontByteBuffer,
                            fontByteBuffer.limit(),
                            fontPointer);
            if (error != FT_Err_Ok) {
                log.error("Failed to create memory face: {}", FT_Error_String(error));
                return false;
            }

            Font font = new Font(fontPath);
            font.fontData = fontByteBuffer;
            font.freeTypeFont = fontPointer;

            fonts.put(fontPath, font);
            if (defaultFont == null) {
                defaultFont = font;
            }
        } catch (IOException e) {
            log.error("Error reading font file: {}", fontPath);
            return false;
        }

        return false;
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
