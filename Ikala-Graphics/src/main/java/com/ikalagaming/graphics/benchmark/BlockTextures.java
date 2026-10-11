package com.ikalagaming.graphics.benchmark;

import lombok.NonNull;

/**
 * Block textures made in code for the test blocks, so the benchmark and the level of detail views
 * show textured blocks without any image files. Each is drawn as {@value #ART} by {@value #ART}
 * pixel art, then scaled up {@value #SCALE} times so linear filtering keeps its pixels sharp.
 * Everything is a pure function of the tile and pixel, so the same tile always comes out the same.
 *
 * <p>Rows go down the block: row 0 is the top of a side face, and the far edge (lowest z) of a top
 * or bottom face, matching the texture coordinates {@link BlockMeshes} gives faces.
 */
public final class BlockTextures {

    /** Pixels along each side of the art. */
    public static final int ART = 16;

    /** How many texture pixels each art pixel covers along each side. */
    public static final int SCALE = 4;

    /** Pixels along each side of a texture. */
    public static final int SIZE = ART * SCALE;

    /** Bytes per pixel: red, green, blue, alpha. */
    public static final int CHANNELS = 4;

    /** The largest channel value. */
    private static final int CHANNEL_MAX = 255;

    /** Rows of grass along the top of a grass block's side. */
    private static final int GRASS_FRINGE = 3;

    /** The most grass drips down below the fringe. */
    private static final int GRASS_DRIP = 3;

    /** Rows per plank. */
    private static final int PLANK_ROWS = 4;

    /** How much of the leaves is holes. */
    private static final double LEAF_HOLES = 0.28;

    /** How see-through water is, as alpha. */
    private static final double WATER_ALPHA = 0.62;

    /** The tiles there are. */
    public enum Tile {
        /** Gray rock with darker flecks. */
        STONE,
        /** Brown earth with pebbles. */
        DIRT,
        /** Grass seen from above. */
        GRASS_TOP,
        /** Dirt with a fringe of grass along the top. */
        GRASS_SIDE,
        /** Horizontal wooden boards. */
        PLANKS,
        /** A tangle of leaves, with holes cut out. */
        LEAVES,
        /** Blue, partly see-through water with ripples. */
        WATER
    }

    /**
     * Draw a tile.
     *
     * @param tile The tile.
     * @return {@value #SIZE} by {@value #SIZE} pixels, rows from the top, red, green, blue and
     *     alpha bytes each.
     */
    public static byte[] pixels(@NonNull Tile tile) {
        final byte[] out = new byte[SIZE * SIZE * CHANNELS];
        final double[] color = new double[CHANNELS];
        for (int y = 0; y < ART; ++y) {
            for (int x = 0; x < ART; ++x) {
                art(tile, x, y, color);
                for (int dy = 0; dy < SCALE; ++dy) {
                    for (int dx = 0; dx < SCALE; ++dx) {
                        final int at = ((y * SCALE + dy) * SIZE + x * SCALE + dx) * CHANNELS;
                        for (int c = 0; c < CHANNELS; ++c) {
                            out[at + c] =
                                    (byte) Math.round(Math.clamp(color[c], 0, 1) * CHANNEL_MAX);
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * One pixel of a tile's art.
     *
     * @param tile The tile.
     * @param x The pixel's column.
     * @param y The pixel's row, from the top.
     * @param out Red, green, blue and alpha, from 0 to 1, written.
     */
    private static void art(@NonNull Tile tile, int x, int y, double @NonNull [] out) {
        final double n = noise(tile, x, y, 0);
        out[3] = 1;
        switch (tile) {
            case STONE -> {
                final double fleck = noise(tile, x, y, 1) < 0.12 ? -0.12 : 0;
                gray(out, 0.5 + 0.08 * (n - 0.5) + fleck);
            }
            case DIRT -> dirt(tile, x, y, n, out);
            case GRASS_TOP -> grass(n, out);
            case GRASS_SIDE -> {
                final int drip = (int) (noise(tile, x, 0, 2) * (GRASS_DRIP + 1));
                if (y < GRASS_FRINGE + drip) {
                    grass(n, out);
                } else {
                    dirt(tile, x, y, n, out);
                }
            }
            case PLANKS -> {
                final int plank = y / PLANK_ROWS;
                // Each plank's end falls somewhere different
                final int end = (int) (noise(tile, 0, plank, 3) * ART);
                final boolean seam = y % PLANK_ROWS == PLANK_ROWS - 1 || x == end;
                final double grain = 0.06 * Math.sin(x * 0.9 + plank * 2.1) + 0.05 * (n - 0.5);
                final double shade = seam ? 0.55 : 1 + grain;
                set(out, 0.62 * shade, 0.45 * shade, 0.26 * shade);
            }
            case LEAVES -> {
                set(out, 0.16 + 0.1 * n, 0.42 + 0.14 * n, 0.12 + 0.06 * n);
                if (noise(tile, x, y, 4) < LEAF_HOLES) {
                    out[3] = 0;
                }
            }
            case WATER -> {
                final double ripple = Math.sin(x * 0.8 + Math.sin(y * 0.6) * 2) > 0.85 ? 0.1 : 0;
                set(out, 0.18 + ripple, 0.34 + ripple, 0.68 + ripple);
                out[3] = WATER_ALPHA;
            }
        }
    }

    /**
     * Earth, with lighter pebbles.
     *
     * @param tile The tile, for the pebbles' noise.
     * @param x The pixel's column.
     * @param y The pixel's row.
     * @param n The pixel's noise.
     * @param out The color, written.
     */
    private static void dirt(@NonNull Tile tile, int x, int y, double n, double @NonNull [] out) {
        final double pebble = noise(tile, x, y, 5) < 0.08 ? 0.12 : 0;
        set(
                out,
                0.45 + 0.08 * (n - 0.5) + pebble,
                0.31 + 0.06 * (n - 0.5) + pebble,
                0.19 + 0.04 * (n - 0.5) + pebble);
    }

    /**
     * Grass.
     *
     * @param n The pixel's noise.
     * @param out The color, written.
     */
    private static void grass(double n, double @NonNull [] out) {
        set(out, 0.3 + 0.08 * (n - 0.5), 0.56 + 0.12 * (n - 0.5), 0.2 + 0.06 * (n - 0.5));
    }

    /**
     * Set a gray.
     *
     * @param out The color, written.
     * @param value The gray.
     */
    private static void gray(double @NonNull [] out, double value) {
        set(out, value, value, value * 0.97);
    }

    /**
     * Set a color, keeping alpha.
     *
     * @param out The color, written.
     * @param red The red.
     * @param green The green.
     * @param blue The blue.
     */
    private static void set(double @NonNull [] out, double red, double green, double blue) {
        out[0] = red;
        out[1] = green;
        out[2] = blue;
    }

    /**
     * A hash of a pixel, spread evenly over 0 to 1.
     *
     * @param tile The tile.
     * @param x The column.
     * @param y The row.
     * @param salt Which of a pixel's values.
     * @return The value.
     */
    private static double noise(@NonNull Tile tile, int x, int y, int salt) {
        int h = tile.ordinal() * 0x632BE5AB + x * 0x85EBCA6B + y * 0xC2B2AE35 + salt * 0x27D4EB2F;
        // The finalizer of MurmurHash3
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h >>> 8) / (double) (1 << 24);
    }

    /** Static helpers only. */
    private BlockTextures() {}
}
