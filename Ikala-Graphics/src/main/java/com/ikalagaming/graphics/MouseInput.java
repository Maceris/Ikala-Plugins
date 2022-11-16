package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

import lombok.Getter;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

/**
 * Handles mouse input for a window.
 */
public class MouseInput {

	/**
	 * The position the mouse was in last time we checked.
	 */
	private final Vector2f previousPos;
	/**
	 * The current position the mouse is in.
	 * 
	 * @return The current position.
	 */
	@Getter
	private final Vector2f currentPos;

	/**
	 * The displacement vector for how a mouse has moved.
	 *
	 * @return the displacement vector.
	 */
	@Getter
	private final Vector2f displaceVector;

	/**
	 * If the left button is currently pressed.
	 *
	 * @return True if the left button is pressed, false if it is not.
	 */
	@Getter
	private boolean leftButtonPressed;

	/**
	 * If the right button is currently pressed.
	 *
	 * @return True if the right button is pressed, false if it is not.
	 */
	@Getter
	private boolean rightButtonPressed;
	/**
	 * Whether or not the mouse is currently in the window.
	 *
	 * @return True if the mouse is in the window, false if it is outside.
	 */
	@Getter
	private boolean inWindow;

	/**
	 * Create a new object with default values.
	 * 
	 * @param windowHandle The handle of the window we are interacting with.
	 */
	public MouseInput(long windowHandle) {
		this.previousPos = new Vector2f(-1, -1);
		this.currentPos = new Vector2f();
		this.displaceVector = new Vector2f();
		this.leftButtonPressed = false;
		this.rightButtonPressed = false;
		this.inWindow = false;

		GLFW.glfwSetCursorPosCallback(windowHandle, (handle, xpos, ypos) -> {
			this.currentPos.x = (float) xpos;
			this.currentPos.y = (float) ypos;
		});
		GLFW.glfwSetCursorEnterCallback(windowHandle,
			(handle, entered) -> this.inWindow = entered);
		GLFW.glfwSetMouseButtonCallback(windowHandle,
			(handle, button, action, mode) -> {
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
	 */
	public void input() {
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