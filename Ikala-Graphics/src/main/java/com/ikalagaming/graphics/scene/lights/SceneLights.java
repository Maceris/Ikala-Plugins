package com.ikalagaming.graphics.scene.lights;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * The scene-wide lighting: the sun and the ambient light. Point lights and spotlights are placed by
 * plugins through {@link com.ikalagaming.graphics.Lights}, and kept in the scene's {@link
 * LightRegistry}.
 */
@Setter
@Getter
public class SceneLights {
    /**
     * The ambient light details.
     *
     * @param ambientLight The color of the ambient light.
     * @return The color of the ambient light.
     */
    @NonNull private AmbientLight ambientLight;

    /**
     * The directional light for the scene.
     *
     * @param dirLight The directional light in the scene.
     * @return The directional light in the scene.
     */
    @NonNull private DirectionalLight dirLight;

    /** Create a new scene light setup without any lights configured. */
    public SceneLights() {
        ambientLight = new AmbientLight();
        dirLight = new DirectionalLight(new Vector3f(1, 1, 1), new Vector3f(0, 1, 0), 0f);
    }
}
