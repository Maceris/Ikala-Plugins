package com.ikalagaming.graphics.scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * A physical sky in place of the skybox: Earth's atmosphere lit by the scene's sun, drawn as the
 * sky and lighting the scene from it, see {@link Scene#setPhysicalSky}. Its light follows the sun:
 * it scales with the sun's color and intensity, goes red near the horizon and dark once the sun is
 * down. The ambient light is not used while it is on.
 *
 * <p>Changes take effect from the next frame.
 */
@Getter
@Setter
public class PhysicalSky {

    /** The sky's light as physics gives it, which {@link #strength} scales. */
    public static final float PHYSICAL_STRENGTH = 1;

    /** A typical albedo of the ground seen around a scene, about grass and soil. */
    private static final float DEFAULT_GROUND_ALBEDO = 0.3f;

    /**
     * How much the sky's light is scaled, 1 for what the atmosphere gives with the sun as bright as
     * it is.
     *
     * @param strength The scale.
     * @return The scale.
     */
    private float strength = PHYSICAL_STRENGTH;

    /**
     * The ground's albedo below the horizon, which lights the atmosphere and the scene from below.
     * Linear.
     *
     * @param groundAlbedo The albedo.
     * @return The albedo.
     */
    @NonNull private Vector3f groundAlbedo = new Vector3f(DEFAULT_GROUND_ALBEDO);

    /**
     * The world height of the ground the atmosphere stands on, in meters. The camera's height above
     * it, never below it, picks how much atmosphere it looks through.
     *
     * @param groundHeight The height.
     * @return The height.
     */
    private double groundHeight;
}
