package com.ikalagaming.graphics;

/**
 * A handle to a water tile in the scene. Holding a handle does not keep the tile alive: it is
 * removed explicitly through {@link Water#remove(WaterHandle)}, or when the plugin that placed it
 * is unloaded. A handle whose tile was removed is stale, and changing it does nothing.
 *
 * @param slot The tile's slot in the scene's water registry.
 * @param generation How many times the slot had been freed when this tile took it. A stale handle
 *     has an older generation than the tile currently in the slot.
 * @see Water
 */
public record WaterHandle(int slot, int generation) {}
