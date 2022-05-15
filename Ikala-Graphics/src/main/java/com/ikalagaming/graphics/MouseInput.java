package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

import lombok.Getter;
import org.joml.Vector2d;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

/**
 * Handles mouse input for a window.
 *
 * @author Ches Burks
 *
 */
public class MouseInput {

	private final Vector2d previousPos;
	private final Vector2d currentPos;

	/**
	 * The displacement vector for how a mouse has moved.
	 *
	 * @return the displacement vector.
	 */
	@Getter
	private final Vector2f displaceVector;

	private boolean inWindow = false;

	/**
	 * If the left button is currently pressed.
	 *
	 * @return True if the left button is pressed, false if it is not.
	 */
	@Getter
	private boolean leftButtonPressed = false;

	/**
	 * If the right button is currently pressed.
	 *
	 * @return True if the right button is pressed, false if it is not.
	 */
	@Getter
	private boolean rightButtonPressed = false;

	/**
	 * Create a new object with default values.
	 */
	public MouseInput() {
		this.previousPos = new Vector2d(-1, -1);
		this.currentPos = new Vector2d(0, 0);
		this.displaceVector = new Vector2f();
	}

	/**
	 * Initialize the mouse input tracking for a given window.
	 *
	 * @param window The window we will be interacting with.
	 */
	public void init(Window window) {
		GLFW.glfwSetCursorPosCallback(window.getWindowHandle(),
			(windowHandle, xpos, ypos) -> {
				this.currentPos.x = xpos;
				this.currentPos.y = ypos;
			});
		GLFW.glfwSetCursorEnterCallback(window.getWindowHandle(),
			(windowHandle, entered) -> this.inWindow = entered);
		GLFW.glfwSetMouseButtonCallback(window.getWindowHandle(),
			(windowHandle, button, action, mode) -> {
				if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
					switch (action) {
						case GLFW_PRESS:
							this.leftButtonPressed = true;
							break;
						case GLFW_RELEASE:
							this.leftButtonPressed = false;
							break;
						case GLFW_REPEAT:
						default:
							break;
					}
				}
				if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
					switch (action) {
						case GLFW_PRESS:
							this.rightButtonPressed = true;
							GLFW.glfwSetInputMode(windowHandle,
								GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
							break;
						case GLFW_RELEASE:
							this.rightButtonPressed = false;
							GLFW.glfwSetInputMode(windowHandle,
								GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
							break;
						case GLFW_REPEAT:
						default:
							break;
					}
				}
			});
	}

	/**
	 * Calculate input for a window.
	 *
	 * @param window The window we are interacting with.
	 */
	public void input(Window window) {
		this.displaceVector.x = 0;
		this.displaceVector.y = 0;
		if (this.previousPos.x > 0 && this.previousPos.y > 0 && this.inWindow) {
			double deltax = this.currentPos.x - this.previousPos.x;
			double deltay = this.currentPos.y - this.previousPos.y;
			boolean rotateX = deltax != 0;
			boolean rotateY = deltay != 0;
			if (rotateX) {
				this.displaceVector.y = (float) deltax;
			}
			if (rotateY) {
				this.displaceVector.x = (float) deltay;
			}
		}
		this.previousPos.x = this.currentPos.x;
		this.previousPos.y = this.currentPos.y;
	}

}