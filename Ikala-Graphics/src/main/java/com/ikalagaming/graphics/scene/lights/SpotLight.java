/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene.lights;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/** A light that acts in a cone. */
@Getter
@Setter
public class SpotLight {
    /**
     * The direction the light is pointing towards.
     *
     * @param coneDirection The new direction.
     * @return The direction.
     */
    @NonNull private Vector3f coneDirection;

    /**
     * The angle where we cut the light off, stored as cos(cut-off angle in radians).
     *
     * @param cutOff The new cutoff, in radians.
     * @return The cutoff in radians.
     */
    @Setter(value = AccessLevel.NONE)
    private float cutOff;

    /**
     * The light information we are using, which we cut off everywhere except the cone.
     *
     * @param pointLight The new light.
     * @return The light information.
     */
    @NonNull private PointLight pointLight;

    /**
     * Create a new spotlight.
     *
     * @param pointLight The point light that describes the properties of the light.
     * @param coneDirection The direction the point light is facing.
     * @param cutOffAngle The angle in degrees we cut off the light at away from the direction.
     */
    public SpotLight(
            @NonNull PointLight pointLight, @NonNull Vector3f coneDirection, float cutOffAngle) {
        this.pointLight = pointLight;
        this.coneDirection = coneDirection;
        setCutOffAngle(cutOffAngle);
    }

    /**
     * Set the cone direction.
     *
     * @param x The x direction.
     * @param y The y direction.
     * @param z The z direction.
     */
    public void setConeDirection(float x, float y, float z) {
        coneDirection.set(x, y, z);
    }

    /**
     * Set the cutoff angle, in degrees. Converted to cos(toRadians(angle in degrees)).
     *
     * @param cutOffAngle The angle in degrees.
     */
    public final void setCutOffAngle(float cutOffAngle) {
        cutOff = (float) Math.cos(Math.toRadians(cutOffAngle));
    }
}
