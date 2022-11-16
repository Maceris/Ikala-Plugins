package com.ikalagaming.graphics.scene.lights;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * A light that acts in a cone.
 */
@Getter
@Setter
public class SpotLight {
	/**
	 * The light information we are using, which we cut off everywhere except
	 * the cone.
	 *
	 * @param pointLight The new light.
	 * @return The light information.
	 */
	private PointLight pointLight;

	/**
	 * The direction the light is pointing towards.
	 *
	 * @param coneDirection The new direction.
	 * @return The direction.
	 */
	private Vector3f coneDirection;

	/**
	 * The angle where we cut the light off, stored as cos(cut-off angle in
	 * radians).
	 *
	 * @param cutOff The new cutoff, in radians.
	 * @return The cutoff in radians.
	 */
	private float cutOff;

	/**
	 * Create a new spotlight.
	 *
	 * @param pointLight The point light that describes the properties of the
	 *            light.
	 * @param coneDirection The direction the point light is facing.
	 * @param cutOffAngle The angle in degrees we cut off the light at away from
	 *            the direction.
	 */
	public SpotLight(PointLight pointLight, Vector3f coneDirection,
		float cutOffAngle) {
		this.pointLight = pointLight;
		this.coneDirection = coneDirection;
		this.setCutOffAngle(cutOffAngle);
	}

	/**
	 * Copy constructor.
	 *
	 * @param spotLight The spotlight to copy.
	 */
	public SpotLight(SpotLight spotLight) {
		this(new PointLight(spotLight.getPointLight()),
			new Vector3f(spotLight.getConeDirection()), 0);
		this.setCutOff(spotLight.getCutOff());
	}

	/**
	 * Set the cutoff angle, in degrees. Converted to cos(toRadians(angle in
	 * degrees)).
	 *
	 * @param cutOffAngle The angle in degrees.
	 */
	public final void setCutOffAngle(float cutOffAngle) {
		this.setCutOff((float) Math.cos(Math.toRadians(cutOffAngle)));
	}
	
	/**
	 * Set the cone direction.
	 *
	 * @param x The x position.
	 * @param y The y position.
	 * @param z The z position.
	 */
	public void setConeDirection(float x, float y, float z) {
		this.coneDirection.set(x, y, z);
	}
}
