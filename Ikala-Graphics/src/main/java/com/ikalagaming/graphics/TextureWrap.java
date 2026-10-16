package com.ikalagaming.graphics;

/**
 * What a texture shows outside its 0 to 1 texture coordinates, chosen when it loads.
 *
 * <ul>
 *   <li>{@link #REPEAT}: the texture tiles, as authored materials expect, like a road texture
 *       repeated along a street. Filtering at an edge blends with the opposite edge.
 *   <li>{@link #CLAMP}: the edge texels stretch on, and filtering never reaches the opposite edge,
 *       which suits images shown whole and tiles that don't wrap, like a block's grass side.
 * </ul>
 *
 * @see Textures
 */
public enum TextureWrap {
    /** The texture tiles. */
    REPEAT,
    /** The edge texels stretch on. */
    CLAMP
}
