package com.ikalagaming.graphics.frontend;

/**
 * A texture handle.
 *
 * @param id A unique ID for the texture.
 * @param width The width of the texture, in pixels.
 * @param height The height of the texture, in pixels.
 */
public record Texture(long id, int width, int height) {}
