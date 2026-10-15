package com.ikalagaming.graphics.graph;

import lombok.NonNull;

/**
 * Each material's color and opacity, as the volume voxelizer reads them: its texture's average
 * where it has one, else its base color, the color decoded to linear light. A snapshot the render
 * thread publishes for the bake workers, so they never read the material cache themselves.
 *
 * @param colors Four values per material index: linear red, green and blue, then opacity.
 * @param version Which snapshot this is; each one with more materials counts up.
 */
public record VolumeMaterials(float @NonNull [] colors, int version) {

    /** Values per material. */
    public static final int VALUES = 4;

    /** No materials yet. */
    public static final VolumeMaterials NONE = new VolumeMaterials(new float[0], 0);

    /**
     * How many materials it has.
     *
     * @return The count.
     */
    public int count() {
        return colors.length / VALUES;
    }

    /**
     * A material's color and opacity.
     *
     * @param material The material's index.
     * @return Its linear red, green and blue and its opacity, or null if it isn't in this snapshot.
     */
    public float[] color(int material) {
        if (material < 0 || material >= count()) {
            return null;
        }
        final float[] out = new float[VALUES];
        System.arraycopy(colors, material * VALUES, out, 0, VALUES);
        return out;
    }
}
