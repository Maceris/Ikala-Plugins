package com.ikalagaming.graphics.scene.lights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Ambient lighting for a scene.
 *
 * @author Ches Burks
 *
 */
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
	private Vector3f color;

	/**
	 * How bright the ambient light is.
	 *
	 * @param intensity The ambient light intensity.
	 * @return The ambient light intensity.
	 */
	private float intensity;
}
