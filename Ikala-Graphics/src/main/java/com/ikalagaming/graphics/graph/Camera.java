package com.ikalagaming.graphics.graph;

import lombok.Getter;
import org.joml.Vector3f;

/**
 * Represents a camera in the world space.
 * 
 * @author Ches Burks
 *
 */
@Getter
public class Camera {

	/**
	 * The position of the camera.
	 *
	 * @return The position vector.
	 */
	private final Vector3f position;

	/**
	 * The rotation of the camera.
	 *
	 * @return The rotation vector.
	 */
	private final Vector3f rotation;

	/**
	 * Creates a new camera with default values.
	 */
	public Camera() {
		this.position = new Vector3f(0, 0, 0);
		this.rotation = new Vector3f(0, 0, 0);
	}

	/**
	 * Creates a camera with supplied position and rotation.
	 * 
	 * @param position The initial position of the camera.
	 * @param rotation The initial rotation of the camera.
	 */
	public Camera(Vector3f position, Vector3f rotation) {
		this.position = position;
		this.rotation = rotation;
	}

	/**
	 * Change the position of the camera by an offset.
	 * 
	 * @param offsetX The offset on the x axis.
	 * @param offsetY The offset on the y axis.
	 * @param offsetZ The offset on the z axis.
	 */
	public void movePosition(float offsetX, float offsetY, float offsetZ) {
		if (offsetZ != 0) {
			this.position.x += (float) Math.sin(Math.toRadians(this.rotation.y))
				* -1.0f * offsetZ;
			this.position.z +=
				(float) Math.cos(Math.toRadians(this.rotation.y)) * offsetZ;
		}
		if (offsetX != 0) {
			this.position.x +=
				(float) Math.sin(Math.toRadians(this.rotation.y - 90)) * -1.0f
					* offsetX;
			this.position.z +=
				(float) Math.cos(Math.toRadians(this.rotation.y - 90))
					* offsetX;
		}
		this.position.y += offsetY;
	}

	/**
	 * Change the rotation of the camera by an offset in degrees.
	 * 
	 * @param offsetX The offset around the x axis.
	 * @param offsetY The offset around the y axis.
	 * @param offsetZ The offset around the z axis.
	 */
	public void moveRotation(float offsetX, float offsetY, float offsetZ) {
		this.rotation.x += offsetX;
		this.rotation.y += offsetY;
		this.rotation.z += offsetZ;
	}

	/**
	 * Set the position of the camera.
	 * 
	 * @param x The new x position.
	 * @param y The new y position.
	 * @param z The new z position.
	 */
	public void setPosition(float x, float y, float z) {
		this.position.x = x;
		this.position.y = y;
		this.position.z = z;
	}

	/**
	 * Set the rotation of the camera.
	 * 
	 * @param x The new x rotation.
	 * @param y The new y rotation.
	 * @param z The new z rotation.
	 */
	public void setRotation(float x, float y, float z) {
		this.rotation.x = x;
		this.rotation.y = y;
		this.rotation.z = z;
	}
}