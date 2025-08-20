package com.ikalagaming.graphics.scene.lights;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** The lighting information for a scene. */
@Getter
public class SceneLights {
    /**
     * The ambient light details.
     *
     * @param ambientLight The color of the ambient light.
     * @return The color of the ambient light.
     */
    @Setter @NonNull private AmbientLight ambientLight;

    /**
     * The directional light for the scene.
     *
     * @param dirLight The directional light in the scene.
     * @return The directional light in the scene.
     */
    @Setter @NonNull private DirectionalLight dirLight;

    /**
     * The list of point lights in a scene.
     *
     * @return The list of point lights in the scene.
     */
    private final List<PointLight> pointLights;

    /**
     * The list of spotlights in a scene.
     *
     * @return The list of spotlights in the scene.
     */
    private final List<SpotLight> spotLights;

    /** Create a new scene light setup without any lights configured. */
    public SceneLights() {
        ambientLight = new AmbientLight();
        pointLights = new ArrayList<>();
        spotLights = new ArrayList<>();
        dirLight = new DirectionalLight(new Vector3f(1, 1, 1), new Vector3f(0, 1, 0), 0f);
    }
}
