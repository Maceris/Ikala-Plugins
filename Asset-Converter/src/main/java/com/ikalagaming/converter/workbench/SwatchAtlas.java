package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.TextureFilter;
import com.ikalagaming.graphics.TextureHandle;
import com.ikalagaming.graphics.TextureWrap;
import com.ikalagaming.graphics.Textures;
import com.ikalagaming.graphics.gui.data.TextureView;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Many small images in one texture, so a large graph's swatches take one bindless slot instead of
 * hundreds. The texture is a grid of square cells; each key gets a cell, and new pixels replace a
 * cell's old ones in place. Pixels are linear half floats, shown encoded to sRGB, texel sharp.
 * Render thread only.
 */
@Slf4j
public class SwatchAtlas {
    /** A cell's size in pixels. */
    public static final int CELL = 64;

    /** Cells across and down the texture. */
    static final int GRID = 16;

    /** Channels per pixel. */
    private static final int CHANNELS = 4;

    /** How swatches are drawn: sharp texels, encoded to sRGB, over a checkerboard by alpha. */
    public static final TextureView VIEW =
            new TextureView(TextureView.Channel.RGBA, 0, 0, true, true, false, true);

    /** Where a key's image is in the texture. */
    public record Cell(float minU, float minV, float maxU, float maxV, float aspect) {}

    /** Uploads the texture and its cells. */
    private final Textures textures;

    /** The texture, made when first needed, or null. */
    private @Nullable TextureHandle texture;

    /** Each key's cell index. */
    private final Map<String, Integer> cells = new HashMap<>();

    /** Each key's image within its cell. */
    private final Map<String, Cell> placed = new HashMap<>();

    /** Cells given back, used first. */
    private final Deque<Integer> free = new ArrayDeque<>();

    /** The next cell never used. */
    private int next;

    /** Whether running out of cells was logged, so it is logged once. */
    private boolean warned;

    /** One cell's pixels, reused. */
    private final ByteBuffer scratch =
            ByteBuffer.allocateDirect(CELL * CELL * CHANNELS * Short.BYTES)
                    .order(ByteOrder.nativeOrder());

    /**
     * Make an empty atlas.
     *
     * @param textures Uploads the texture and its cells.
     */
    public SwatchAtlas(@NonNull Textures textures) {
        this.textures = textures;
    }

    /**
     * The texture, for drawing.
     *
     * @return The texture, or null before anything was put in.
     */
    public @Nullable TextureHandle getTexture() {
        return texture;
    }

    /**
     * Where a key's image is.
     *
     * @param key The key.
     * @return Its cell, or null if it has none.
     */
    public @Nullable Cell cell(@NonNull String key) {
        return placed.get(key);
    }

    /**
     * Put an image in a key's cell, scaled to fit with its shape kept, nearest texel.
     *
     * @param key The key, which gets a cell if it has none.
     * @param rgba Linear red, green, blue and alpha for each pixel, rows from the top.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return The cell, or null if the atlas is full.
     */
    public @Nullable Cell put(@NonNull String key, float @NonNull [] rgba, int width, int height) {
        final Integer index = cells.computeIfAbsent(key, k -> take());
        if (index < 0) {
            cells.remove(key);
            if (!warned) {
                log.warn(
                        "The swatch atlas is full, {} cells; later nodes show no swatch",
                        GRID * GRID);
                warned = true;
            }
            return null;
        }
        if (texture == null) {
            texture =
                    textures.load(
                            null,
                            Format.R16G16B16A16_SFLOAT,
                            CELL * GRID,
                            CELL * GRID,
                            TextureWrap.CLAMP,
                            TextureFilter.PIXELS);
        }
        final float scale = Math.min((float) CELL / width, (float) CELL / height);
        final int shownWidth = Math.clamp(Math.round(width * scale), 1, CELL);
        final int shownHeight = Math.clamp(Math.round(height * scale), 1, CELL);
        scratch.clear();
        for (int y = 0; y < CELL; ++y) {
            for (int x = 0; x < CELL; ++x) {
                if (x >= shownWidth || y >= shownHeight) {
                    for (int c = 0; c < CHANNELS; ++c) {
                        scratch.putShort(Float.floatToFloat16(0));
                    }
                    continue;
                }
                final int sourceX = Math.min(width - 1, (int) (x / scale));
                final int sourceY = Math.min(height - 1, (int) (y / scale));
                final int at = (sourceY * width + sourceX) * CHANNELS;
                for (int c = 0; c < CHANNELS; ++c) {
                    scratch.putShort(Float.floatToFloat16(rgba[at + c]));
                }
            }
        }
        scratch.flip();
        final int left = index % GRID * CELL;
        final int top = index / GRID * CELL;
        textures.update(texture, scratch, left, top, CELL, CELL);
        final float size = CELL * GRID;
        final Cell cell =
                new Cell(
                        left / size,
                        top / size,
                        (left + shownWidth) / size,
                        (top + shownHeight) / size,
                        (float) width / height);
        placed.put(key, cell);
        return cell;
    }

    /**
     * Give a key's cell back.
     *
     * @param key The key.
     */
    public void remove(@NonNull String key) {
        final Integer index = cells.remove(key);
        placed.remove(key);
        if (index != null) {
            free.push(index);
        }
    }

    /**
     * The keys that have cells.
     *
     * @return The keys, a copy.
     */
    public java.util.Set<String> keys() {
        return new java.util.HashSet<>(cells.keySet());
    }

    /**
     * A cell to use.
     *
     * @return Its index, or -1 if there are none left.
     */
    private int take() {
        if (!free.isEmpty()) {
            return free.pop();
        }
        return next < GRID * GRID ? next++ : -1;
    }

    /** Release the texture and forget every cell. */
    public void close() {
        textures.release(texture);
        texture = null;
        cells.clear();
        placed.clear();
        free.clear();
        next = 0;
    }
}
