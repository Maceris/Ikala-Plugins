package com.ikalagaming.graphics.scene.lights;

import lombok.Getter;
import lombok.Setter;

/**
 * The lighting information for a scene.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
public class SceneLight {
	/**
	 * The ambient light details.
	 *
	 * @param ambientLight The color of the ambient light.
	 * @return The color of the ambient light.
	 */
	private AmbientLight ambientLight;

	/**
	 * The list of point lights in a scene.
	 *
	 * @param pointLightList The list of point lights in the scene.
	 * @return The list of point lights in the scene.
	 */
	private PointLight[] pointLightList;

	/**
	 * The list of spot lights in a scene.
	 *
	 * @param spotLightList The list of spot lights in the scene.
	 * @return The list of spot lights in the scene.
	 */
	private SpotLight[] spotLightList;

	/**
	 * The directional light for the scene.
	 *
	 * @param directionalLight The directional light in the scene.
	 * @return The directional light in the scene.
	 */
	private DirectionalLight directionalLight;

	/**
	 * Create a new scene light setup without any lights configured.
	 */
	public SceneLight() {}
}
