package com.ikalagaming.graphics.graph;

import com.ikalagaming.graphics.SceneItem;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bundles together the projection and world matrix.
 *
 * @author Ches Burks
 *
 */
public class Transformation {

	private final Matrix4f modelViewMatrix;
	private final Matrix4f projectionMatrix;
	private final Matrix4f viewMatrix;

	/**
	 * Create a new transformation.
	 */
	public Transformation() {
		this.modelViewMatrix = new Matrix4f();
		this.projectionMatrix = new Matrix4f();
		this.viewMatrix = new Matrix4f();
	}

	/**
	 * Calculate and return the view matrix for a model based on a cameras view
	 * matrix.
	 *
	 * @param sceneItem The item we are calculating the view matrix for.
	 * @param cameraViewMatrix The cameras view matrix.
	 * @return The view matrix for a model.
	 */
	public Matrix4f getModelViewMatrix(SceneItem sceneItem,
		Matrix4f cameraViewMatrix) {

		Vector3f rotation = sceneItem.getRotation();
		this.modelViewMatrix.identity().translate(sceneItem.getPosition())
			.rotateX((float) Math.toRadians(-rotation.x))
			.rotateY((float) Math.toRadians(-rotation.y))
			.rotateZ((float) Math.toRadians(-rotation.z))
			.scale(sceneItem.getScale());
		Matrix4f currentView = new Matrix4f(cameraViewMatrix);
		return currentView.mul(this.modelViewMatrix);
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
	 * Calculate the view matrix for a camera.
	 *
	 * @param camera The camera to calculate the view matrix for.
	 * @return The appropriate view matrix.
	 */
	public Matrix4f getViewMatrix(Camera camera) {
		Vector3f cameraPos = camera.getPosition();
		Vector3f rotation = camera.getRotation();

		this.viewMatrix.identity();
		/**
		 * Rotate first, then translation, so that the we rotate the camera
		 * around itself and not the origin.
		 */
		this.viewMatrix
			.rotate((float) Math.toRadians(rotation.x), new Vector3f(1, 0, 0))
			.rotate((float) Math.toRadians(rotation.y), new Vector3f(0, 1, 0));
		this.viewMatrix.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		return this.viewMatrix;
	}
}