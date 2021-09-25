package com.ikalagaming.graphics;

import com.ikalagaming.graphics.graph.Camera;

import lombok.Getter;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Handles the position and movement of the camera.
 *
 * @author Ches Burks
 *
 */
public class CameraManager {

	private static final float MOUSE_SENSITIVITY = 0.2f;
	/**
	 * The speed in world units that the camera moves per second.
	 */
	private static final float MOVE_SPEED_PER_SECOND = 5f;
	private final MouseInput mouseInput;
	/**
	 * The camera for the window.
	 *
	 * @return The camera.
	 */
	@Getter
	private final Camera camera;
	private final Vector3f cameraInc;
	private final Window window;

	/**
	 * Create a new camera manager for the given window.
	 *
	 * @param window The window the camera renders to.
	 */
	public CameraManager(Window window) {
		this.mouseInput = new MouseInput();
		this.mouseInput.init(window);
		this.camera = new Camera();
		this.cameraInc = new Vector3f();
		this.window = window;
	}

	/**
	 * Handle input that relates to the camera, prepare camera for movement
	 * appropriately.
	 */
	public void processInput() {
		this.mouseInput.input(this.window);
		this.cameraInc.set(0, 0, 0);
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_W)) {
			this.cameraInc.z = -1;
		}
		else if (this.window.isKeyPressed(GLFW.GLFW_KEY_S)) {
			this.cameraInc.z = 1;
		}
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_A)) {
			this.cameraInc.x = -1;
		}
		else if (this.window.isKeyPressed(GLFW.GLFW_KEY_D)) {
			this.cameraInc.x = 1;
		}
		if (this.window.isKeyPressed(GLFW.GLFW_KEY_Z)) {
			this.cameraInc.y = -1;
		}
		else if (this.window.isKeyPressed(GLFW.GLFW_KEY_X)) {
			this.cameraInc.y = 1;
		}

	}

	/**
	 * Actually reposition the camera based on how much time has passed.
	 *
	 * @param deltaTime The fractional part of a second since the last update.
	 */
	public void updateCamera(float deltaTime) {
		// Update camera position
		this.camera.movePosition(
			this.cameraInc.x * CameraManager.MOVE_SPEED_PER_SECOND * deltaTime,
			this.cameraInc.y * CameraManager.MOVE_SPEED_PER_SECOND * deltaTime,
			this.cameraInc.z * CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);

		// Update camera based on mouse
		if (this.mouseInput.isRightButtonPressed()) {
			Vector2f rotVec = this.mouseInput.getDisplaceVector();
			this.camera.moveRotation(rotVec.x * CameraManager.MOUSE_SENSITIVITY,
				rotVec.y * CameraManager.MOUSE_SENSITIVITY, 0);
		}
	}
}
