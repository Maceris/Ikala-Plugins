package com.ikalagaming.graphics.scene.lights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/** A point light. */
@Getter
@Setter
@AllArgsConstructor
public class PointLight {

    /**
     * The color of the point light.
     *
     * @param color The new color.
     * @return The color.
     */
    @NonNull private Vector3f color;

    /**
     * The position of the point light.
     *
     * @param position The new position.
     * @return The current position.
     */
    @NonNull private Vector3f position;

    /**
     * The intensity of the point light, measured in candela per square meter (cd/m^2). Point lights
     * don't have any area, so this is not actually a reasonable unit, but we want to use the same
     * units for all types of lights. We just have to hack around this a bit when dealing with
     * punctual lights.
     *
     * @param intensity The new intensity.
     * @return The intensity of the point light.
     */
    private float intensity;
}
