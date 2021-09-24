package com.ikalagaming.graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bundles together the projection and world matrix.
 *
 * @author Ches Burks
 *
 */
public class Transformation {

	private final Matrix4f projectionMatrix;
	private final Matrix4f worldMatrix;

	/**
	 * Create a new transformation.
	 */
	public Transformation() {
		this.worldMatrix = new Matrix4f();
		this.projectionMatrix = new Matrix4f();
	}

	/**
	 * Calculate and return the projection matrix.
	 *
	 * @param fov Field of view, in radians.
	 * @param width The width of the screen.
	 * @param height The height of the screen.
	 * @param zNear The distance from the camera to the near plane.
	 * @param zFar The distance from the camera to the far plane.
	 * @return The appropriate projection matrix.
	 */
	public final Matrix4f getProjectionMatrix(float fov, float width,
		float height, float zNear, float zFar) {
		float aspectRatio = width / height;
		this.projectionMatrix.identity();
		this.projectionMatrix.perspective(fov, aspectRatio, zNear, zFar);
		return this.projectionMatrix;
	}

	/**
	 * Calculate and return the world matrix.
	 *
	 * @param offset The offset matrix for the world.
	 * @param rotation The rotation matrix for the world.
	 * @param scale The scale for the world.
	 * @return The appropriate world matrix.
	 */
	public Matrix4f getWorldMatrix(Vector3f offset, Vector3f rotation,
		float scale) {
		this.worldMatrix.identity().translate(offset)
			.rotateX((float) Math.toRadians(rotation.x))
			.rotateY((float) Math.toRadians(rotation.y))
			.rotateZ((float) Math.toRadians(rotation.z)).scale(scale);
		return this.worldMatrix;
	}
}