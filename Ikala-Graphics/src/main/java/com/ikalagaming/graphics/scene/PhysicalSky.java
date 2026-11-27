package com.ikalagaming.graphics.scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * A physical sky in place of the skybox: Earth's atmosphere lit by the scene's sun, drawn as the
 * sky and lighting the scene from it, see {@link Scene#setPhysicalSky}. Its light follows the sun:
 * it scales with the sun's color and intensity, goes red near the horizon and dark once the sun is
 * down. The ambient light is not used while it is on. A sky can be lit from a direction of its own
 * instead, see {@link #sunDirection}.
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

    /**
     * The sky's own light, which does not come from the sun: airglow, starlight and twilight. It is
     * a luminance the same from every direction above the horizon, in cd/m^2, and not scaled by the
     * sun or {@link #strength}; it lights the scene as the sky does and adds to the sky drawn. 0 by
     * default. Linear.
     *
     * @param glow The radiance.
     * @return The radiance.
     */
    @NonNull private Vector3f glow = new Vector3f();

    /**
     * How bright the stars are: the illuminance a star of magnitude 0 gives, in lux, about 2.5e-6.
     * Stars are drawn down to about magnitude 6.5, dimmed by the air they shine through; 0 draws
     * none.
     *
     * @param starIlluminance The illuminance.
     * @return The illuminance.
     */
    private float starIlluminance;

    /**
     * How the stars are turned: the rotation from world directions to directions on the star
     * sphere, which turns once a day about the celestial pole.
     *
     * @param starRotation The rotation.
     * @return The rotation.
     */
    @NonNull private Quaternionf starRotation = new Quaternionf();

    /**
     * The direction the sky is lit along, the way the light travels as a {@link
     * com.ikalagaming.graphics.scene.lights.DirectionalLight}'s direction is, or null for the
     * scene's sun's. With one, the sky is drawn and lights the scene as if the sun were there, and
     * the sun's light is colored by the air as if it came from there, while it still shines along
     * its own direction at its own intensity: a scene can be lit from low behind it without the sky
     * turning to twilight. Not normalized. Null by default.
     *
     * @param sunDirection The direction, or null to follow the sun.
     * @return The direction, or null when the sky follows the sun.
     */
    @Nullable private Vector3f sunDirection;
}
