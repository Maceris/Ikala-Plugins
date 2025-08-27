package com.ikalagaming.graphics.frontend;

/**
 * A texture handle.
 *
 * @param id A unique ID for the texture.
 * @param handle A bindless texture handle. Will be 0 if this is not a bindless texture.
 * @param width The width of the texture, in pixels.
 * @param height The height of the texture, in pixels.
 */
public record Texture(long id, long handle, int width, int height) {}
