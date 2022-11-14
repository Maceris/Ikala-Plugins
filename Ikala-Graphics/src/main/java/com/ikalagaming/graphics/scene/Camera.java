package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.Utils;

import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Represents a camera in the world space.
 */
public class Camera {

	/**
	 * The position of the camera.
	 *
	 * @return The position vector.
	 */
	@Getter
	private final Vector3f position;

	/**
	 * The rotation of the camera.
	 *
	 * @return The rotation vector.
	 */
	@Getter
	private final Vector2f rotation;

	/**
	 * The direction the camera is facing.
	 * 
	 * @return The direction
	 */
	@Getter
	private Vector3f direction;
	/**
	 * The inverse of the view matrix.
	 * 
	 * @return The inverse view matrix.
	 */
	@Getter
	private Matrix4f invViewMatrix;
	/**
	 * The direction that is considered right, relative to the camera.
	 */
	private Vector3f right;
	/**
	 * The direction that is considered up, relative to the camera.
	 */
	private Vector3f up;
	/**
	 * The view matrix.
	 * 
	 * @return The current view matrix.
	 */
	@Getter
	private Matrix4f viewMatrix;

	/**
	 * Creates a new camera with default values.
	 */
	public Camera() {
		direction = new Vector3f();
		right = new Vector3f();
		up = new Vector3f();
		position = new Vector3f();
		viewMatrix = new Matrix4f();
		invViewMatrix = new Matrix4f();
		rotation = new Vector2f();
	}

	/**
	 * Add rotation to the current rotation.
	 * 
	 * @param x The x (roll) rotation increment.
	 * @param y The y (pitch) rotation increment.
	 */
	public void addRotation(float x, float y) {
		rotation.add(x, y);
		this.rotation.x = Utils.clampFloat(this.rotation.x, -90, 90);
		recalculate();
	}

	/**
	 * Move backwards by the given increment.
	 * 
	 * @param inc The amount to move by.
	 */
	public void moveBackwards(float inc) {
		viewMatrix.positiveZ(direction).negate().mul(inc);
		position.sub(direction);
		recalculate();
	}

	/**
	 * Move down by the given increment.
	 * 
	 * @param inc The amount to move by.
	 */
	public void moveDown(float inc) {
		viewMatrix.positiveY(up).mul(inc);
		position.sub(up);
		recalculate();
	}

	/**
	 * Move forwards by the given increment.
	 * 
	 * @param inc The amount to move by.
	 */
	public void moveForward(float inc) {
		viewMatrix.positiveZ(direction).negate().mul(inc);
		position.add(direction);
		recalculate();
	}

	/**
	 * Move left by the given increment.
	 * 
	 * @param inc The amount to move by.
	 */
	public void moveLeft(float inc) {
		viewMatrix.positiveX(right).mul(inc);
		position.sub(right);
		recalculate();
	}

	/**
	 * Move right by the given increment.
	 * 
	 * @param inc The amount to move by.
	 */
	public void moveRight(float inc) {
		viewMatrix.positiveX(right).mul(inc);
		position.add(right);
		recalculate();
	}

	/**
	 * Move up by the given increment.
	 * 
	 * @param inc The amount to move by.
	 */
	public void moveUp(float inc) {
		viewMatrix.positiveY(up).mul(inc);
		position.add(up);
		recalculate();
	}

	/**
	 * Recalculate the view and inverse view matrices.
	 */
	private void recalculate() {
		viewMatrix.identity().rotateX(rotation.x).rotateY(rotation.y)
			.translate(-position.x, -position.y, -position.z);
		invViewMatrix.set(viewMatrix).invert();
	}

	/**
	 * Set the position of the camera.
	 *
	 * @param x The new x position.
	 * @param y The new y position.
	 * @param z The new z position.
	 */
	public void setPosition(float x, float y, float z) {
		position.set(x, y, z);
		recalculate();
	}

	/**
	 * Set the rotation of the camera.
	 *
	 * @param x The new x (roll) rotation.
	 * @param y The new y (pitch) rotation.
	 */
	public void setRotation(float x, float y) {
		rotation.set(x, y);
		this.rotation.x = Utils.clampFloat(this.rotation.x, -90, 90);
		recalculate();
	}
}