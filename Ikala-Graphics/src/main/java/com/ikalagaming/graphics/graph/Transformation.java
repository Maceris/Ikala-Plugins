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
	private final Matrix4f orthoMatrix;

	/**
	 * Create a new transformation.
	 */
	public Transformation() {
		this.modelViewMatrix = new Matrix4f();
		this.projectionMatrix = new Matrix4f();
		this.viewMatrix = new Matrix4f();
		this.orthoMatrix = new Matrix4f();
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
	 * Get an orthographic projection matrix.
	 *
	 * @param left The distance from the center to the left frustum edge.
	 * @param right The distance from the center to the right frustum edge.
	 * @param bottom The distance from the center to the bottom frustum edge.
	 * @param top The distance from the center to the top frustum edge.
	 *
	 * @return The newly calculated matrix.
	 */
	public final Matrix4f getOrthoProjectionMatrix(float left, float right,
		float bottom, float top) {
		this.orthoMatrix.identity();
		this.orthoMatrix.setOrtho2D(left, right, bottom, top);
		return this.orthoMatrix;
	}

	/**
	 * Orthographic projection matrix of a model.
	 *
	 * @param gameItem The game item to render.
	 * @param orthographicMatrix The orthographic projection transformation
	 *            matrix.
	 * @return The transformed item matrix.
	 */
	public Matrix4f getOrtoProjModelMatrix(SceneItem gameItem,
		Matrix4f orthographicMatrix) {
		Vector3f rotation = gameItem.getRotation();
		Matrix4f modelMatrix = new Matrix4f();
		modelMatrix.identity().translate(gameItem.getPosition())
			.rotateX((float) Math.toRadians(-rotation.x))
			.rotateY((float) Math.toRadians(-rotation.y))
			.rotateZ((float) Math.toRadians(-rotation.z))
			.scale(gameItem.getScale());
		Matrix4f orthoMatrixCurr = new Matrix4f(orthographicMatrix);
		orthoMatrixCurr.mul(modelMatrix);
		return orthoMatrixCurr;
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