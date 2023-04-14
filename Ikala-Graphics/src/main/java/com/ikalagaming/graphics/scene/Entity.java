/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Something that is part of the 3D scene.
 */
@Getter
public class Entity {
	/**
	 * Numbers for a unique entity name.
	 */
	public static final AtomicInteger NEXT_ID = new AtomicInteger();
	/**
	 * A unique ID.
	 *
	 * @return The unique ID.
	 */
	private final String entityID;
	/**
	 * A unique ID for the model associated to this entity.
	 *
	 * @return The model ID.
	 */
	private final String modelID;
	/**
	 * Animation data associated with the entity.
	 *
	 * @param animationData The new animation data.
	 * @return The animation data.
	 */
	@Setter
	private AnimationData animationData;
	/**
	 * The combined translation, rotation, and scale transformations.
	 *
	 * @return The model matrix.
	 */
	private Matrix4f modelMatrix;
	/**
	 * The transformation matrix.
	 *
	 * @return The position matrix.
	 */
	private Vector3f position;
	/**
	 * The rotation, as a quaternion to prevent gimbal lock.
	 *
	 * @return The rotation.
	 */
	private Quaternionf rotation;
	/**
	 * Used to modify rotations. We just keep around an instance to avoid object
	 * creation every time we rotate a model.
	 */
	private Quaternionf delta;

	/**
	 * The scale factor.
	 *
	 * @param scale The new scale.
	 * @return The scale.
	 */
	@Setter
	private float scale;

	/**
	 * Create a new entity.
	 *
	 * @param id The ID of the entity.
	 * @param modelId The ID of the model associated with this entity.
	 */
	public Entity(@NonNull String id, @NonNull String modelId) {
		this.entityID = id;
		this.modelID = modelId;
		this.modelMatrix = new Matrix4f();
		this.position = new Vector3f();
		this.rotation = new Quaternionf();
		this.delta = new Quaternionf();
		this.scale = 1;
	}

	/**
	 * Add to the rotation.
	 *
	 * @param x The x component of the rotation axis.
	 * @param y The y component of the rotation axis.
	 * @param z The z component of the rotation axis.
	 * @param angle The angle in radians.
	 */
	public void addRotation(float x, float y, float z, float angle) {
		this.delta.fromAxisAngleRad(x, y, z, angle);
		this.delta.mul(this.rotation, this.rotation);
	}

	/**
	 * Set the position.
	 *
	 * @param x The new x position.
	 * @param y The new y position.
	 * @param z The new z position.
	 */
	public final void setPosition(float x, float y, float z) {
		this.position.x = x;
		this.position.y = y;
		this.position.z = z;
	}

	/**
	 * Set the rotation.
	 *
	 * @param x The x component of the rotation axis.
	 * @param y The y component of the rotation axis.
	 * @param z The z component of the rotation axis.
	 * @param angle The angle in radians.
	 */
	public void setRotation(float x, float y, float z, float angle) {
		this.rotation.fromAxisAngleRad(x, y, z, angle);
	}

	/**
	 * Update the model matrix based on the current position, rotation, and
	 * scale.
	 */
	public void updateModelMatrix() {
		this.modelMatrix.translationRotateScale(this.position, this.rotation,
			this.scale);
	}
}