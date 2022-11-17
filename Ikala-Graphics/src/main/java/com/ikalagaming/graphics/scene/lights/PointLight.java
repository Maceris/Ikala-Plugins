package com.ikalagaming.graphics.scene.lights;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * A point light.
 */
@Getter
@Setter
public class PointLight {

	/**
	 * Attenuation info for a light. Controls the way light falls off with
	 * distance. Calculated using the formula:
	 *
	 * {@code 1.0/(constant + linear * dist + exponent * dist^2)}.
	 */
	@Getter
	@Setter
	@AllArgsConstructor
	public static class Attenuation {
		/**
		 * The constant component.
		 *
		 * @param constant The constant component.
		 * @return The constant fall off.
		 */
		private float constant;
		/**
		 * The exponent component.
		 *
		 * @param exponent The exponent component.
		 * @return The exponential fall off with respect to distance.
		 */
		private float exponent;
		/**
		 * The linear component.
		 *
		 * @param linear The linear component.
		 * @return The linear fall off with respect to distance.
		 */
		private float linear;
		
	}
	/**
	 * Information about how the light attenuates.
	 *
	 * @param attenuation The new attenuation information.
	 * @return The attenuation information.
	 */
	private Attenuation attenuation;
	
	/**
	 * The color of the point light.
	 *
	 * @param color The new color.
	 * @return The color.
	 */
	private Vector3f color;
	/**
	 * The intensity of the point light, between 0 and 1.
	 *
	 * @param intensity The new intensity.
	 * @return The intensity of the point light.
	 */
	protected float intensity;
	/**
	 * The position of the point light.
	 *
	 * @param position The new position.
	 * @return The current position.
	 */
	private Vector3f position;

	/**
	 * Create a new point light with a constant attenuation.
	 *
	 * @param color The color of the light.
	 * @param position The position of the light.
	 * @param intensity The intensity of the light.
	 */
	public PointLight(Vector3f color, Vector3f position, float intensity) {
		this.attenuation = new Attenuation(0, 1, 0);
		this.color = color;
		this.position = position;
		this.intensity = intensity;
	}

	/**
	 * Create a new point light.
	 *
	 * @param color The color of the light.
	 * @param position The position of the light.
	 * @param intensity The intensity of the light.
	 * @param attenuation The attenuation info for the light.
	 */
	public PointLight(Vector3f color, Vector3f position, float intensity,
		Attenuation attenuation) {
		this(color, position, intensity);
		this.attenuation = attenuation;
	}
	
	/**
	 * Set the position.
	 *
	 * @param x The x position.
	 * @param y The y position.
	 * @param z The z position.
	 */
	public void setPosition(float x, float y, float z) {
		this.position.set(x, y, z);
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