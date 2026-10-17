package com.ikalagaming.graphics.lod;

/**
 * What a material looks like averaged over its surface, for level of detail far enough away that
 * its texture can't be told apart: its base color times its texture's average color, in linear
 * light, with its roughness, metallic and specular. Groups that far out blend these across the
 * surface they stand for instead of keeping one material a triangle. Safe from any thread.
 */
public interface MaterialAverages {

    /** Values a material's average holds. */
    int VALUES = 6;

    /** Where the linear red is; green and blue follow. */
    int RED = 0;

    /** Where the roughness is. */
    int ROUGHNESS = 3;

    /** Where the metallic is. */
    int METALLIC = 4;

    /** Where the specular is. */
    int SPECULAR = 5;

    /**
     * A material's average.
     *
     * @param material The material's index.
     * @return Its {@link #VALUES} values, or null if it isn't known.
     */
    float[] average(int material);

    /**
     * How much of a material's surface its texture covers, for cut out materials whose holes let
     * light through: its texture's average alpha. Volumes standing for it stop that share of light.
     *
     * @param material The material's index.
     * @return The share covered, 0 to 1; all of it unless said otherwise.
     */
    default float coverage(int material) {
        return 1;
    }
}
