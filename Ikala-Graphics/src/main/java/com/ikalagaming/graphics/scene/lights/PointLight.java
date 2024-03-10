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

/** A point light. */
@Getter
@Setter
public class PointLight {
    /**
     * Attenuation info for a light. Controls the way light falls off with distance. Calculated
     * using the formula:
     *
     * <p>{@code 1.0/(constant + linear * dist + exponent * dist^2)}.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    public static class Attenuation {
        /**
         * The constant component.
         *
         * @param constant The constant component.
         * @return The constant fall off.
         */
        private float constant;

        /**
         * The exponent component.
         *
         * @param exponent The exponent component.
         * @return The exponential fall off with respect to distance.
         */
        private float exponent;

        /**
         * The linear component.
         *
         * @param linear The linear component.
         * @return The linear fall off with respect to distance.
         */
        private float linear;
    }

    /**
     * Information about how the light attenuates.
     *
     * @param attenuation The new attenuation information.
     * @return The attenuation information.
     */
    @NonNull private Attenuation attenuation;

    /**
     * The color of the point light.
     *
     * @param color The new color.
     * @return The color.
     */
    @NonNull private Vector3f color;

    /**
     * The intensity of the point light, between 0 and 1.
     *
     * @param intensity The new intensity.
     * @return The intensity of the point light.
     */
    private float intensity;

    /**
     * The position of the point light.
     *
     * @param position The new position.
     * @return The current position.
     */
    @NonNull private Vector3f position;

    /**
     * Create a new point light with a constant attenuation.
     *
     * @param color The color of the light.
     * @param position The position of the light.
     * @param intensity The intensity of the light.
     */
    public PointLight(@NonNull Vector3f color, @NonNull Vector3f position, float intensity) {
        attenuation = new Attenuation(0, 1, 0);
        this.color = color;
        this.position = position;
        this.intensity = intensity;
    }

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
     * Set the position.
     *
     * @param x The x position.
     * @param y The y position.
     * @param z The z position.
     */
    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }
}
