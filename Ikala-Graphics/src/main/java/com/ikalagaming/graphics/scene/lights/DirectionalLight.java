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
     * The intensity of the light, measured in candela per square meter (cd/m^2).
     *
     * @param intensity The new intensity of the light.
     * @return The intensity of the light.
     */
    private float intensity;
}
