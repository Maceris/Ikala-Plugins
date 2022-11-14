package com.ikalagaming.graphics.scene;

import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Something that is part of the 3D scene.
 */
@Getter
public class Entity {
	/**
	 * A unique ID.
	 *
	 * @return The unique ID.
	 */
	private final String id;
	/**
	 * A unique ID for the model associated to this entity.
	 *
	 * @return The model ID.
	 */
	private final String modelId;
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
	 * The scale factor.
	 *
	 * @param scale The new scale.
	 * @return The scale.
	 */
	@Setter
	private float scale;
	/**
	 * Animation data associated with the entity.
	 *
	 * @param animationData The new animation data.
	 * @return The animation data.
	 */
	@Setter
	private AnimationData animationData;

	/**
	 * Create a new entity.
	 *
	 * @param id The ID of the entity.
	 * @param modelId The ID of the model associated with this entity.
	 */
	public Entity(String id, String modelId) {
		this.id = id;
		this.modelId = modelId;
		this.modelMatrix = new Matrix4f();
		this.position = new Vector3f();
		this.rotation = new Quaternionf();
		this.scale = 1;
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
