package com.ikalagaming.graphics.scene.debug;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * A 3D axis-aligned bounding box.
 *
 * @author Ches Burks
 */
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Box {
    /**
     * The color of the box.
     *
     * @param color The new color.
     * @return The color.
     */
    @NonNull private Vector3f color;

    /**
     * The position of the center of the box.
     *
     * @param position The new position.
     * @return The current position.
     */
    @NonNull private Vector3f position;

    /**
     * The x, y, and z scale of the box.
     *
     * @param size The new size.
     * @return The current size.
     */
    @NonNull private Vector3f size;

    /**
     * Text to show above the box, which will be the same color as the box. May be null if not
     * applicable.
     *
     * @param text The new text to show above the box.
     * @return The text to show above the box.
     */
    private String text;
}
