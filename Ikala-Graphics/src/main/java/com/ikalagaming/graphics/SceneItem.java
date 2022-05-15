package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Mesh;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * A mesh with position, scale, and rotation that shows up on the scene graph.
 *
 * @author Ches Burks
 *
 */
@Getter
public class SceneItem {
	/**
	 * The mesh to render for this item.
	 *
	 * @return The mesh for this item.
	 */
	@Setter
	private Mesh mesh;

	/**
	 * The position of the item.
	 *
	 * @return The position vector.
	 */
	private final Vector3f position;

	/**
	 * The 3D rotation of the item.
	 *
	 * @return The rotation vector.
	 */
	private final Vector3f rotation;
	/**
	 * The scale of the item.
	 *
	 * @param scale The new scale.
	 * @return The scale.
	 */
	@Setter
	private float scale;

	/**
	 * Construct a new scene item without a mesh.
	 */
	public SceneItem() {
		this.position = new Vector3f(0, 0, 0);
		this.scale = 1;
		this.rotation = new Vector3f(0, 0, 0);
	}

	/**
	 * Create a new scene item for the given mesh. Defaults to a zero position
	 * and rotation, and scale of 1.
	 *
	 * @param mesh The mesh for the item.
	 */
	public SceneItem(Mesh mesh) {
		this();
		this.mesh = mesh;
	}

	/**
	 * Clean up the mesh.
	 */
	public void cleanup() {
		if (this.mesh != null) {
			this.mesh.cleanUp();
		}
	}

	/**
	 * Set the new position values.
	 *
	 * @param x The x position.
	 * @param y The y position.
	 * @param z The z position.
	 */
	public void setPosition(float x, float y, float z) {
		this.position.x = x;
		this.position.y = y;
		this.position.z = z;
	}

	/**
	 * Rotation around each axis in degrees.
	 *
	 * @param x Rotation around the x axis in degrees.
	 * @param y Rotation around the y axis in degrees.
	 * @param z Rotation around the z axis in degrees.
	 */
	public void setRotation(float x, float y, float z) {
		this.rotation.x = x;
		this.rotation.y = y;
		this.rotation.z = z;
	}

}