package com.ikalagaming.graphics.scene.debug;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A line segment.
 *
 * @param from One end, in world space.
 * @param to The other end, in world space.
 * @param color The packed RGBA color.
 * @param onTop Whether to draw over everything instead of depth testing.
 */
public record DebugLine(@NonNull Vector3dc from, @NonNull Vector3dc to, int color, boolean onTop)
        implements DebugShape {

    /** Copy the vectors so the shape can't change after it is made. */
    public DebugLine {
        from = new Vector3d(from);
        to = new Vector3d(to);
    }

    /**
     * A depth tested line segment.
     *
     * @param from One end, in world space.
     * @param to The other end, in world space.
     * @param color The packed RGBA color.
     */
    public DebugLine(@NonNull Vector3dc from, @NonNull Vector3dc to, int color) {
        this(from, to, color, false);
    }
}
