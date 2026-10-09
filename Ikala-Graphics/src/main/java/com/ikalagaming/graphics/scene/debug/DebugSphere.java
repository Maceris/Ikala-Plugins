package com.ikalagaming.graphics.scene.debug;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A sphere, drawn as three circles around its center, one in each axis plane.
 *
 * @param center The center, in world space.
 * @param radius The radius.
 * @param color The packed RGBA color.
 * @param onTop Whether to draw over everything instead of depth testing.
 */
public record DebugSphere(@NonNull Vector3dc center, double radius, int color, boolean onTop)
        implements DebugShape {

    /** Copy the vectors so the shape can't change after it is made. */
    public DebugSphere {
        center = new Vector3d(center);
    }

    /**
     * A depth tested sphere.
     *
     * @param center The center, in world space.
     * @param radius The radius.
     * @param color The packed RGBA color.
     */
    public DebugSphere(@NonNull Vector3dc center, double radius, int color) {
        this(center, radius, color, false);
    }
}
