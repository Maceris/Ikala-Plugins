package com.ikalagaming.graphics;

/**
 * A handle to a skinned instance: one posed copy of a set of skinned meshes in the scene, like a
 * character. Holding a handle does not keep the instance alive: it is removed through {@link
 * Skins#remove(SkinnedInstanceHandle)}, or when the plugin that placed it unloads. A handle whose
 * instance was removed is stale, and changing it does nothing.
 *
 * @param slot The instance's slot.
 * @param generation How many times the slot had been freed when this instance took it.
 * @see Skins
 */
public record SkinnedInstanceHandle(int slot, int generation) {}
