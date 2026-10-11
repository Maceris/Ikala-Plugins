package com.ikalagaming.graphics.lod;

import lombok.NonNull;

/**
 * How level of detail clusters are textured: by projecting the material's texture along the
 * triangle's main axis, one repeat per block. The coordinates are unwrapped, so they interpolate
 * across a triangle, and the shader takes their fraction; on a block face they agree with the
 * texture coordinates the blocks themselves have (see {@code BlockMeshes}): on a side, u runs left
 * to right seen from outside and v down from the top; on a top or bottom, u follows x and v follows
 * z.
 *
 * <p>{@code scene_lod.vert} does the same; keep the two in step.
 */
public final class LodTexturing {

    /** The up axis. */
    private static final int Y = 1;

    /**
     * The axis a normal points along most, preferring up and down on ties.
     *
     * @param normal The normal.
     * @return 0, 1 or 2.
     */
    public static int axis(float @NonNull [] normal) {
        final float x = Math.abs(normal[0]);
        final float y = Math.abs(normal[1]);
        final float z = Math.abs(normal[2]);
        if (y >= x && y >= z) {
            return Y;
        }
        return x >= z ? 0 : 2;
    }

    /**
     * Unwrapped texture coordinates of a point.
     *
     * @param normal The triangle's normal.
     * @param position The point, in blocks.
     * @param out Receives u and v; their fractions are the texture coordinates.
     */
    public static void uv(
            float @NonNull [] normal, double @NonNull [] position, double @NonNull [] out) {
        final int axis = axis(normal);
        final boolean positive = normal[axis] > 0;
        final double x = position[0];
        final double y = position[1];
        final double z = position[2];
        switch (axis) {
            case 0 -> {
                // Seen from +x, right is -z; from -x, +z
                out[0] = positive ? -z : z;
                out[1] = -y;
            }
            case Y -> {
                out[0] = x;
                out[1] = z;
            }
            default -> {
                // Seen from +z, right is +x; from -z, -x
                out[0] = positive ? x : -x;
                out[1] = -y;
            }
        }
    }

    /**
     * The texture coordinate an unwrapped one samples.
     *
     * @param unwrapped The unwrapped coordinate.
     * @return Its fraction, from 0 up to 1.
     */
    public static double fraction(double unwrapped) {
        return unwrapped - Math.floor(unwrapped);
    }

    /** Static helpers only. */
    private LodTexturing() {}
}
