package com.ikalagaming.graphics.scene.lights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/** Ambient lighting for a scene. */
@Getter
@Setter
@AllArgsConstructor
public class AmbientLight {
    /**
     * The base color of the ambient light.
     *
     * @param color The base color.
     * @return The base color.
     */
    @NonNull private Vector3f color;

    /**
     * How bright the ambient light is, measured in candela per square meter (cd/m^2). This is
     * visually like adding emissivity to everything in the scene.
     *
     * @param intensity The ambient light intensity.
     * @return The ambient light intensity.
     */
    private float intensity;

    /** Create an ambient light with default values. */
    public AmbientLight() {
        this(new Vector3f(1.0f, 1.0f, 1.0f), 0.0f);
    }
}
