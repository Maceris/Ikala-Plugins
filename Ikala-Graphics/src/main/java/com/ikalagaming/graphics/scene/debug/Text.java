package com.ikalagaming.graphics.scene.debug;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Floating text in the world.
 *
 * @author Ches Burks
 */
@Getter
@Setter
@AllArgsConstructor
public class Text {
    /**
     * The color of the text.
     *
     * @param color The new color.
     * @return The color.
     */
    @NonNull private Vector3f color;

    /**
     * The position of the center of the text.
     *
     * @param position The new position.
     * @return The current position.
     */
    @NonNull private Vector3f position;

    /**
     * Text to show.
     *
     * @param The new text to show.
     * @return The text to show.
     */
    @NonNull private String value;
}
