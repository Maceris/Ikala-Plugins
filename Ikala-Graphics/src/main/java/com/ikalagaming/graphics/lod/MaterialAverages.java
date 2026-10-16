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
}
