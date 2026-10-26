package com.ikalagaming.graphics;

/**
 * A handle to a medium volume in the scene. Holding a handle does not keep the volume alive: it is
 * removed explicitly through {@link Media#remove(MediumHandle)}, or when the plugin that placed it
 * is unloaded. A handle whose volume was removed is stale, and changing it does nothing.
 *
 * @param slot The volume's slot in the scene's medium registry.
 * @param generation How many times the slot had been freed when this volume took it. A stale handle
 *     has an older generation than the volume currently in the slot.
 * @see Media
 */
public record MediumHandle(int slot, int generation) {}
