package com.ikalagaming.graphics;

/**
 * A handle to a particle effect a plugin loaded from a file, see {@link
 * Particles#loadEffect(java.nio.file.Path)}. Emitters spawned from it follow the file when it is
 * reloaded. Holding a handle does not keep the effect: it goes when unloaded or when its plugin
 * unloads, and then the handle is stale, while emitters spawned from it keep its last version.
 *
 * @param slot The effect's slot in the scene's effect library.
 * @param generation How many times the slot had been freed when this effect took it.
 * @see Particles
 */
public record EffectHandle(int slot, int generation) {}
