package com.ikalagaming.graphics;

/**
 * A handle to a particle emitter in the scene. Holding a handle does not keep the emitter alive: it
 * is removed explicitly through {@link Particles#stop(EmitterHandle)}, or when the plugin that
 * placed it is unloaded. A handle whose emitter was removed is stale, and changing it does nothing.
 * Particles already spawned live out their lives either way.
 *
 * @param slot The emitter's slot in the scene's emitter registry.
 * @param generation How many times the slot had been freed when this emitter took it. A stale
 *     handle has an older generation than the emitter currently in the slot.
 * @see Particles
 */
public record EmitterHandle(int slot, int generation) {}
