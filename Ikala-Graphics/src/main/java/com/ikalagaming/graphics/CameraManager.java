package com.ikalagaming.graphics;

import com.ikalagaming.graphics.scene.Camera;

import imgui.ImGui;
import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

/**
 * Handles the position and movement of the camera.
 */
public class CameraManager {

	private static final float MOUSE_SENSITIVITY = 0.02f;
	/**
	 * The speed in world units that the camera moves per second.
	 */
	private static final float MOVE_SPEED_PER_SECOND = 5f;
	/**
	 * The camera for the window.
	 *
	 * @return The camera.
	 */
	@Getter
	private final Camera camera;
	/**
	 * The window we are managing the camera for.
	 */
	private final Window window;

	/**
	 * Create a new camera manager for the given window.
	 *
	 * @param camera The camera we are managing.
	 * @param window The window the camera renders to.
	 */
	public CameraManager(@NonNull Camera camera, @NonNull Window window) {
		this.camera = camera;
		this.window = window;
	}

	/**
	 * Actually reposition the camera based on how much time has passed.
	 *
	 * @param deltaTime The fractional part of a second since the last update.
	 */
	public void updateCamera(float deltaTime) {
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_W)) {
			this.camera
				.moveForward(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_S)) {
			this.camera
				.moveBackwards(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_A)) {
			this.camera
				.moveLeft(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_D)) {
			this.camera
				.moveRight(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
			this.camera
				.moveDown(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_SPACE)) {
			this.camera.moveUp(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}

		// Update camera based on mouse
		if (this.window.getMouseInput().isRightButtonPressed()) {
			if (!ImGui.getIO().getWantCaptureMouse()) {
				Vector2f rotVec = this.window.getMouseInput().getDisplVec();
				this.camera.addRotation(
					rotVec.x * CameraManager.MOUSE_SENSITIVITY,
					rotVec.y * CameraManager.MOUSE_SENSITIVITY);
			}
		}
	}
}
