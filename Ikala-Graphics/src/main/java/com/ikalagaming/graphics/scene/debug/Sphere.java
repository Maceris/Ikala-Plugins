package com.ikalagaming.graphics.scene.debug;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * A colored sphere.
 *
 * @author Ches Burks
 */
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Sphere {
    /**
     * The color of the sphere.
     *
     * @param color The new color.
     * @return The color.
     */
    @NonNull private Vector3f color;

    /**
     * The position of the center of the sphere.
     *
     * @param position The new position.
     * @return The current position.
     */
    @NonNull private Vector3f position;

    /**
     * The size of the sphere in world units.
     *
     * @param size The new size.
     * @return The size.
     */
    private float size = 1;

    /**
     * Text to show above the sphere, which will be the same color as the sphere. May be null if not
     * applicable.
     *
     * @param The new text to show above the sphere.
     * @return The text to show above the sphere.
     */
    private String text;
}
