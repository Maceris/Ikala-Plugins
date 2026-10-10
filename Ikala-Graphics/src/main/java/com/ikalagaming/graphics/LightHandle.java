package com.ikalagaming.graphics;

/**
 * A handle to a point or spot light in the scene. Holding a handle does not keep the light alive:
 * it is removed explicitly through {@link Lights#remove(LightHandle)}, or when the plugin that
 * placed it is unloaded. A handle whose light was removed is stale, and changing it does nothing.
 *
 * @param slot The light's slot in the scene's light registry.
 * @param generation How many times the slot had been freed when this light took it. A stale handle
 *     has an older generation than the light currently in the slot.
 * @see Lights
 */
public record LightHandle(int slot, int generation) {}
