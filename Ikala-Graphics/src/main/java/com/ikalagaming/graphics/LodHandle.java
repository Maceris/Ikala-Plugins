package com.ikalagaming.graphics;

/**
 * A handle to a level of detail world on the GPU, from {@link Lods#add}. Holding it does not keep
 * the world alive: it is removed through {@link Lods#remove}, or when the plugin that added it is
 * unloaded, and then the handle is stale and draws nothing.
 *
 * @param id The world's id in the renderer.
 */
public record LodHandle(long id) {}
