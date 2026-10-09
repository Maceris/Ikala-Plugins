package com.ikalagaming.graphics;

import lombok.NonNull;

/**
 * A handle to a texture on the GPU, along with the facts about it that never change. Holding a
 * handle does not keep the texture alive: textures are released explicitly through {@link
 * Textures#release(TextureHandle)}, or when the plugin that loaded them is unloaded. A handle whose
 * texture was released is stale, and renders as the default white texture.
 *
 * @param slot The texture's slot in the bindless texture array, which is how shaders refer to it.
 * @param generation How many times the slot had been released when this texture took it. A stale
 *     handle has an older generation than the texture currently in the slot.
 * @param width The width of the texture, in pixels.
 * @param height The height of the texture, in pixels.
 * @param format The format of the texture.
 * @see Textures
 */
public record TextureHandle(
        int slot, int generation, int width, int height, @NonNull Format format) {}
