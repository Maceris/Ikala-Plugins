package com.ikalagaming.graphics.scene.debug;

import lombok.NonNull;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A rectangular prism, which can be rotated, so it also works for oriented bounding boxes.
 *
 * @param center The center, in world space.
 * @param halfExtents Half the size along each of the box's own axes.
 * @param rotation The rotation of the box around its center.
 * @param color The packed RGBA color.
 * @param onTop Whether to draw over everything instead of depth testing.
 */
public record DebugBox(
        @NonNull Vector3dc center,
        @NonNull Vector3dc halfExtents,
        @NonNull Quaterniondc rotation,
        int color,
        boolean onTop)
        implements DebugShape {

    /** Copy the vectors so the shape can't change after it is made. */
    public DebugBox {
        center = new Vector3d(center);
        halfExtents = new Vector3d(halfExtents);
        rotation = new Quaterniond(rotation);
    }

    /**
     * A depth tested, axis aligned box.
     *
     * @param center The center, in world space.
     * @param halfExtents Half the size along each axis.
     * @param color The packed RGBA color.
     */
    public DebugBox(@NonNull Vector3dc center, @NonNull Vector3dc halfExtents, int color) {
        this(center, halfExtents, new Quaterniond(), color, false);
    }

    /**
     * A depth tested, axis aligned box between two corners.
     *
     * @param min The corner with the smallest coordinates, in world space.
     * @param max The corner with the largest coordinates, in world space.
     * @param color The packed RGBA color.
     * @return The box.
     */
    public static DebugBox fromCorners(@NonNull Vector3dc min, @NonNull Vector3dc max, int color) {
        Vector3d center = new Vector3d(min).add(max).mul(0.5);
        Vector3d halfExtents = new Vector3d(max).sub(min).mul(0.5);
        return new DebugBox(center, halfExtents, color);
    }
}
