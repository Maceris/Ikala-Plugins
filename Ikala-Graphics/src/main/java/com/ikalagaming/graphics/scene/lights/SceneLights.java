package com.ikalagaming.graphics.scene.lights;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The lighting information for a scene.
 */
@Getter
public class SceneLights {
	/**
	 * The ambient light details.
	 *
	 * @param ambientLight The color of the ambient light.
	 * @return The color of the ambient light.
	 */
	@Setter
	private AmbientLight ambientLight;

	/**
	 * The list of point lights in a scene.
	 *
	 * @return The list of point lights in the scene.
	 */
	private List<PointLight> pointLights;

	/**
	 * The list of spot lights in a scene.
	 *
	 * @return The list of spot lights in the scene.
	 */
	private List<SpotLight> spotLights;

	/**
	 * The directional light for the scene.
	 *
	 * @param directionalLight The directional light in the scene.
	 * @return The directional light in the scene.
	 */
	@Setter
	private DirectionalLight directionalLight;

	/**
	 * Create a new scene light setup without any lights configured.
	 */
	public SceneLights() {
		ambientLight = new AmbientLight();
		pointLights = new ArrayList<>();
		spotLights = new ArrayList<>();
		directionalLight = new DirectionalLight(new Vector3f(1, 1, 1),
			new Vector3f(0, 1, 0), 1.0f);
	}
}
