package com.ikalagaming.graphics;

/**
 * A handle to a persistent debug shape, for removing it later.
 *
 * @param id A unique ID for the shape.
 * @see DebugDraw#add(com.ikalagaming.graphics.scene.debug.DebugShape)
 */
public record DebugShapeHandle(long id) {}
