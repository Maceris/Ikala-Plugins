package com.ikalagaming.factory.world.gen;

import com.ikalagaming.factory.world.gen.density.DensityNode;
import com.ikalagaming.factory.world.gen.density.Styles;

import lombok.NonNull;

/**
 * One region's terrain shape and where it applies, as a terrain style file gives it. A world type
 * lists the styles it uses, and {@link Styles} combines them into its terrain.
 *
 * @param id The data ID.
 * @param shape True for a shape style, blended into the base surface; false for an overlay.
 * @param op What an overlay does, null for a shape style.
 * @param priority When an overlay applies, lowest first; ties go by ID.
 * @param weight How strongly the style applies, 0 to 1.
 * @param density The style's terrain, already sampled on its own grid if it has one.
 * @param cell The style's own sampling grid along x, y and z, or null for the world's.
 */
public record TerrainStyle(
        @NonNull String id,
        boolean shape,
        Styles.Op op,
        int priority,
        @NonNull DensityNode weight,
        @NonNull DensityNode density,
        int[] cell) {

    /** What a style's weight is available as, as a parameter: this prefix and the style's ID. */
    public static final String PARAMETER_PREFIX = "style:";

    /**
     * The style as {@link Styles} uses it.
     *
     * @return The style.
     */
    public Styles.Style toStyle() {
        return new Styles.Style(id, weight, density, op);
    }

    /**
     * The parameter name the style's weight is available under.
     *
     * @return {@code style:} and the ID.
     */
    public String parameterName() {
        return PARAMETER_PREFIX + id;
    }
}
