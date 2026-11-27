package com.ikalagaming.converter.texgraph;

import lombok.NonNull;

/**
 * Normal maps from heights, made exactly as the old blockNormals tool made them, so baked maps
 * match its files byte for byte: the heights are spread to 0..1 between the plane's lowest and
 * highest point, holes take the mean, and a Sobel filter that wraps at the edges gives the slope.
 * Normals are tangent space with green up the texture. The arithmetic is float and in the tool's
 * order on purpose; changing it changes bakes.
 */
final class Normals {
    /** Coverage below this is a hole, which doesn't count toward the heights. */
    static final float HOLE = 0.5f;

    /** A height range smaller than this is flat. */
    private static final float FLAT = 1e-6f;

    /** Not made. */
    private Normals() {}

    /**
     * Normals from heights.
     *
     * @param height The heights, one channel.
     * @param coverage Where the surface is, one channel; below {@value #HOLE} is a hole.
     * @param strength How steep the bumps get: the slope across the full height range, per pixel.
     * @param normalize Whether to spread the heights to 0..1 first, as the old tool did.
     * @return Unit normals, three channels.
     */
    static Plane make(
            @NonNull Plane height, @NonNull Plane coverage, float strength, boolean normalize) {
        final int width = height.getWidth();
        final int rows = height.getHeight();
        final float[] heights = new float[width * rows];
        float sum = 0;
        int counted = 0;
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (int y = 0; y < rows; ++y) {
            for (int x = 0; x < width; ++x) {
                final int at = y * width + x;
                if (coverage.get(x, y, 0) < HOLE) {
                    heights[at] = Float.NaN;
                    continue;
                }
                final float value = height.get(x, y, 0);
                heights[at] = value;
                sum += value;
                ++counted;
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
        }
        final float mean = counted > 0 ? sum / counted : 0;
        final float range = high - low;
        for (int i = 0; i < heights.length; ++i) {
            final float value = Float.isNaN(heights[i]) ? mean : heights[i];
            if (normalize) {
                heights[i] = range > FLAT ? (value - low) / range : 0.5f;
            } else {
                heights[i] = value;
            }
        }
        final Plane out = new Plane(width, rows, 3);
        for (int y = 0; y < rows; ++y) {
            for (int x = 0; x < width; ++x) {
                // Rising to the right, and rising down the image
                final float dx =
                        (at(heights, width, rows, x + 1, y - 1)
                                        + 2 * at(heights, width, rows, x + 1, y)
                                        + at(heights, width, rows, x + 1, y + 1)
                                        - at(heights, width, rows, x - 1, y - 1)
                                        - 2 * at(heights, width, rows, x - 1, y)
                                        - at(heights, width, rows, x - 1, y + 1))
                                / 8;
                final float dy =
                        (at(heights, width, rows, x - 1, y + 1)
                                        + 2 * at(heights, width, rows, x, y + 1)
                                        + at(heights, width, rows, x + 1, y + 1)
                                        - at(heights, width, rows, x - 1, y - 1)
                                        - 2 * at(heights, width, rows, x, y - 1)
                                        - at(heights, width, rows, x + 1, y - 1))
                                / 8;
                // Green is up the texture, against the image's rows, so rising down tilts it up
                float nx = -dx * strength;
                float ny = dy * strength;
                float nz = 1;
                final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= length;
                ny /= length;
                nz /= length;
                out.set(x, y, 0, nx);
                out.set(x, y, 1, ny);
                out.set(x, y, 2, nz);
            }
        }
        return out;
    }

    /**
     * A height, wrapping around the plane.
     *
     * @param heights The heights.
     * @param width Pixels across.
     * @param rows Pixels down.
     * @param x The column, which may be off the plane.
     * @param y The row, which may be off the plane.
     * @return The height.
     */
    private static float at(float[] heights, int width, int rows, int x, int y) {
        return heights[Math.floorMod(y, rows) * width + Math.floorMod(x, width)];
    }

    /**
     * A normal component as a byte, as normal map files store it.
     *
     * @param component The component, -1 to 1.
     * @return The byte.
     */
    static int encode(float component) {
        return Math.round((component * 0.5f + 0.5f) * Srgb.MAX_BYTE);
    }
}
