package com.ikalagaming.graphics.scene.debug;

import com.ikalagaming.graphics.gui.util.Color;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Text pinned to a point in the world, for debugging: drawn on the screen over the scene, centered
 * where the point appears, the same size however far away it is, and never hidden behind the scene.
 * Points behind the camera or off the screen show nothing. The position is copied when the label is
 * made, so changing the vector passed in afterward doesn't move it.
 *
 * @param position The point, in world space.
 * @param text What it says; it may span lines.
 * @param color The text's packed RGBA color, like {@link Color#rgba(int, int, int, int)}.
 * @see com.ikalagaming.graphics.DebugDraw#label(DebugLabel)
 */
public record DebugLabel(@NonNull Vector3dc position, @NonNull String text, int color) {

    /** Copy the position so the label can't move after it is made. */
    public DebugLabel {
        position = new Vector3d(position);
    }

    /**
     * White text.
     *
     * @param position The point, in world space.
     * @param text What it says.
     */
    public DebugLabel(@NonNull Vector3dc position, @NonNull String text) {
        this(position, text, Color.WHITE);
    }
}
