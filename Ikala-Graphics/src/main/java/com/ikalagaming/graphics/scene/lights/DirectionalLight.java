/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene.lights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/** A directional light, infinitely far away with no attenuation. */
@Getter
@Setter
@AllArgsConstructor
public class DirectionalLight {
    /**
     * The color of the light. x, y, and z correspond to r, g, and b respectively.
     *
     * @param color The new color.
     * @return The color of the light.
     */
    @NonNull private Vector3f color;

    /**
     * The direction the light is coming from.
     *
     * @param direction The direction the light comes from.
     * @return The direction of the light.
     */
    @NonNull private Vector3f direction;

    /**
     * The intensity of the light, between 0 and 1.
     *
     * @param intensity The new intensity of the light.
     * @return The intensity of the light.
     */
    private float intensity;

    /**
     * Set the color.
     *
     * @param r The red component.
     * @param g The green component.
     * @param b The blue component.
     */
    public void setColor(float r, float g, float b) {
        color.set(r, g, b);
    }

    /**
     * Set the direction.
     *
     * @param x The x direction.
     * @param y The y direction.
     * @param z The z direction.
     */
    public void setDirection(float x, float y, float z) {
        direction.set(x, y, z);
    }
}
