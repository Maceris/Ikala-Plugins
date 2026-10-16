package com.ikalagaming.graphics;

/**
 * A handle to an instance of a level of detail model, from {@link Lods#place}. Holding it does not
 * keep the instance alive: it is removed through {@link Lods#remove(LodInstanceHandle)}, with its
 * model, or when the plugin that placed it is unloaded, and then the handle is stale and does
 * nothing.
 *
 * @param id The instance's id in the renderer.
 */
public record LodInstanceHandle(long id) {}
