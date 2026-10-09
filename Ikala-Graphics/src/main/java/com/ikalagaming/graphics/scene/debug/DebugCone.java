package com.ikalagaming.graphics.scene.debug;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A cone opening out from its apex, drawn as the circle around its base plus lines from the apex.
 * Matches a spot light's area of effect.
 *
 * @param apex The tip, in world space.
 * @param direction The direction the cone opens toward. It doesn't need to be normalized.
 * @param length The distance from the apex to the base.
 * @param angle The angle between the axis and the side of the cone, in radians.
 * @param color The packed RGBA color.
 * @param onTop Whether to draw over everything instead of depth testing.
 */
public record DebugCone(
        @NonNull Vector3dc apex,
        @NonNull Vector3dc direction,
        double length,
        double angle,
        int color,
        boolean onTop)
        implements DebugShape {

    /** Copy the vectors so the shape can't change after it is made. */
    public DebugCone {
        apex = new Vector3d(apex);
        direction = new Vector3d(direction);
    }

    /**
     * A depth tested cone.
     *
     * @param apex The tip, in world space.
     * @param direction The direction the cone opens toward.
     * @param length The distance from the apex to the base.
     * @param angle The angle between the axis and the side of the cone, in radians.
     * @param color The packed RGBA color.
     */
    public DebugCone(
            @NonNull Vector3dc apex,
            @NonNull Vector3dc direction,
            double length,
            double angle,
            int color) {
        this(apex, direction, length, angle, color, false);
    }
}
