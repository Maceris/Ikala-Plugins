package com.ikalagaming.graphics;

/**
 * How a texture is sampled when it is larger on screen than it is, chosen when it loads.
 *
 * <ul>
 *   <li>{@link #SMOOTH}: texels blend into each other, as photographs and painted materials expect.
 *   <li>{@link #PIXELS}: each texel stays a sharp square up close, as pixel art expects, like a
 *       16x16 block texture across a meter. Far away, where a texel is smaller than a pixel, mip
 *       levels still blend, so it doesn't shimmer.
 * </ul>
 *
 * @see Textures
 */
public enum TextureFilter {
    /** Texels blend when magnified. */
    SMOOTH,
    /** Texels stay sharp when magnified. */
    PIXELS
}
