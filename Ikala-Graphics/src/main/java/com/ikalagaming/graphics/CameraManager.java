package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import com.ikalagaming.graphics.scene.Camera;

import lombok.Getter;
import org.joml.Vector2f;

/**
 * Handles the position and movement of the camera.
 */
public class CameraManager {

	private static final float MOUSE_SENSITIVITY = 0.2f;
	/**
	 * The speed in world units that the camera moves per second.
	 */
	private static final float MOVE_SPEED_PER_SECOND = 5f;
	/**
	 * Mouse input information relating to the camera.
	 */
	private final MouseInput mouseInput;
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
	 * @param window The window the camera renders to.
	 */
	public CameraManager(Window window) {
		this.mouseInput = new MouseInput(window.getWindowHandle());
		this.camera = new Camera();
		this.window = window;
	}

	/**
	 * Actually reposition the camera based on how much time has passed.
	 *
	 * @param deltaTime The fractional part of a second since the last update.
	 */
	public void updateCamera(float deltaTime) {
		this.mouseInput.input();

		if (this.window.isKeyPressed(GLFW_KEY_W)) {
			this.camera
				.moveForward(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW_KEY_S)) {
			this.camera
				.moveBackwards(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW_KEY_A)) {
			this.camera
				.moveLeft(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW_KEY_D)) {
			this.camera
				.moveRight(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
			this.camera
				.moveDown(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}
		if (this.window.isKeyPressed(GLFW_KEY_SPACE)) {
			this.camera.moveUp(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
		}

		// Update camera based on mouse
		if (this.mouseInput.isRightButtonPressed()) {
			Vector2f rotVec = this.mouseInput.getDisplaceVector();
			this.camera.addRotation(rotVec.x * CameraManager.MOUSE_SENSITIVITY,
				rotVec.y * CameraManager.MOUSE_SENSITIVITY);
		}
	}
}
