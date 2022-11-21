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

/**
 * Ambient lighting for a scene.
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
	@NonNull
	private Vector3f color;
	/**
	 * How bright the ambient light is.
	 *
	 * @param intensity The ambient light intensity.
	 * @return The ambient light intensity.
	 */
	private float intensity;

	/**
	 * Create an ambient light with default values.
	 */
	public AmbientLight() {
		this(new Vector3f(1.0f, 1.0f, 1.0f), 1.0f);
	}

	/**
	 * Set the color.
	 *
	 * @param r The red component.
	 * @param g The green component.
	 * @param b The blue component.
	 */
	public void setColor(float r, float g, float b) {
		this.color.set(r, g, b);
	}
}