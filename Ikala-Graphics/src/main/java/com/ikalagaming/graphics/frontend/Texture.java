package com.ikalagaming.graphics.frontend;

/**
 * A texture handle.
 *
 * @param width The width of the texture, in pixels.
 * @param height The height of the texture, in pixels.
 * @param info Details for the texture.
 */
@Deprecated
public record Texture(int width, int height, TextureInfo info) {}
