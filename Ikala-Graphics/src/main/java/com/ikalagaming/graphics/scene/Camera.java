/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
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
	 * A constant value used to clamp rotation, pre-calculated for convenience.
	 */
	private static final float TWO_PI = (float) Math.PI * 2;
	/**
	 * Used to calculate movements.
	 */
	private Vector3f temp;
	/**
	 * The inverse of the view matrix.
	 *
	 * @return The inverse view matrix.
	 */
	@Getter
	private Matrix4f invViewMatrix;
	/**
	 * The position of the camera.
	 *
	 * @return The position vector.
	 */
	@Getter
	private Vector3f position;
	/**
	 * The rotation of the camera.
	 *
	 * @return The rotation vector.
	 */
	@Getter
	private Vector2f rotation;
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
		this.temp = new Vector3f();
		this.position = new Vector3f();
		this.viewMatrix = new Matrix4f();
		this.invViewMatrix = new Matrix4f();
		this.rotation = new Vector2f();
	}

	/**
	 * Add rotation to the current rotation.
	 *
	 * @param x The x (pitch) rotation increment.
	 * @param y The y (yaw) rotation increment.
	 */
	public void addRotation(float x, float y) {
		this.rotation.add(x, y);
		this.recalculate();
	}

	/**
	 * Move backwards by the given increment.
	 *
	 * @param inc The amount to move by.
	 */
	public void moveBackwards(float inc) {
		this.invViewMatrix.transformDirection(0, 0, 1, this.temp);
		this.position.add(this.temp.mul(inc));
		this.recalculate();
	}

	/**
	 * Move down by the given increment.
	 *
	 * @param inc The amount to move by.
	 */
	public void moveDown(float inc) {
		this.invViewMatrix.transformDirection(0, 1, 0, this.temp);
		this.position.sub(this.temp.mul(inc));
		this.recalculate();
	}

	/**
	 * Move forwards by the given increment.
	 *
	 * @param inc The amount to move by.
	 */
	public void moveForward(float inc) {
		this.invViewMatrix.transformDirection(0, 0, 1, this.temp);
		this.position.sub(this.temp.mul(inc));
		this.recalculate();
	}

	/**
	 * Move left by the given increment.
	 *
	 * @param inc The amount to move by.
	 */
	public void moveLeft(float inc) {
		this.invViewMatrix.transformDirection(1, 0, 0, this.temp);
		this.position.sub(this.temp.mul(inc));
		this.recalculate();
	}

	/**
	 * Move right by the given increment.
	 *
	 * @param inc The amount to move by.
	 */
	public void moveRight(float inc) {
		this.invViewMatrix.transformDirection(1, 0, 0, this.temp);
		this.position.add(this.temp.mul(inc));
		this.recalculate();
	}

	/**
	 * Move up by the given increment.
	 *
	 * @param inc The amount to move by.
	 */
	public void moveUp(float inc) {
		this.invViewMatrix.transformDirection(0, 1, 0, this.temp);
		this.position.add(this.temp.mul(inc));
		this.recalculate();
	}

	/**
	 * Recalculate the view and inverse view matrices.
	 */
	private void recalculate() {
		this.rotation.x = Utils.clampFloat(this.rotation.x,
			(float) Math.toRadians(-90), (float) Math.toRadians(90));

		this.rotation.y %= Camera.TWO_PI;
		this.rotation.y = (this.rotation.y + Camera.TWO_PI) % Camera.TWO_PI;

		this.viewMatrix.identity().rotateX(this.rotation.x)
			.rotateY(this.rotation.y)
			.translate(-this.position.x, -this.position.y, -this.position.z);
		this.invViewMatrix.set(this.viewMatrix).invert();
	}

	/**
	 * Set the position of the camera.
	 *
	 * @param x The new x position.
	 * @param y The new y position.
	 * @param z The new z position.
	 */
	public void setPosition(float x, float y, float z) {
		this.position.set(x, y, z);
		this.recalculate();
	}

	/**
	 * Set the rotation of the camera.
	 *
	 * @param x The new x (pitch) rotation.
	 * @param y The new y (yaw) rotation.
	 */
	public void setRotation(float x, float y) {
		this.rotation.set(x, y);
		this.recalculate();
	}
}