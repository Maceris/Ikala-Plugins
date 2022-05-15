package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.DirectionalLight;
import com.ikalagaming.graphics.graph.PointLight;
import com.ikalagaming.graphics.graph.SpotLight;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

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
	 * The ambient light color.
	 *
	 * @param ambientLight The color of the ambient light.
	 * @return The color of the ambient light.
	 */
	private Vector3f ambientLight;

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
