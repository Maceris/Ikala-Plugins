package com.ikalagaming.graphics.scene.debug;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * An arrow, with a head at the end it points to. The head is sized from the arrow's length.
 *
 * @param from The tail, in world space.
 * @param to The tip, in world space.
 * @param color The packed RGBA color.
 * @param onTop Whether to draw over everything instead of depth testing.
 */
public record DebugArrow(@NonNull Vector3dc from, @NonNull Vector3dc to, int color, boolean onTop)
        implements DebugShape {

    /** Copy the vectors so the shape can't change after it is made. */
    public DebugArrow {
        from = new Vector3d(from);
        to = new Vector3d(to);
    }

    /**
     * A depth tested arrow.
     *
     * @param from The tail, in world space.
     * @param to The tip, in world space.
     * @param color The packed RGBA color.
     */
    public DebugArrow(@NonNull Vector3dc from, @NonNull Vector3dc to, int color) {
        this(from, to, color, false);
    }
}
