package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_2;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.glfwSetCursorEnterCallback;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;

import lombok.Getter;
import org.joml.Vector2f;

/** Handles mouse input for a window. */
public class MouseInput {
    /**
     * The current position the mouse is in.
     *
     * @return The current position.
     */
    @Getter private final Vector2f currentPos;

    /**
     * The displacement vector for how a mouse has moved.
     *
     * @return the displacement vector.
     */
    @Getter private final Vector2f displVec;

    /**
     * Whether the mouse is currently in the window.
     *
     * @return True if the mouse is in the window, false if it is outside.
     */
    @Getter private boolean inWindow;

    /**
     * If the left button is currently pressed.
     *
     * @return True if the left button is pressed, false if it is not.
     */
    @Getter private boolean leftButtonPressed;

    /** The position the mouse was in last time we checked. */
    private final Vector2f previousPos;

    /**
     * If the right button is currently pressed.
     *
     * @return True if the right button is pressed, false if it is not.
     */
    @Getter private boolean rightButtonPressed;

    /**
     * Create a new object with default values.
     *
     * @param windowHandle The handle of the window we are interacting with.
     */
    public MouseInput(long windowHandle) {
        // TODO(ches) maybe we should use our new GUI system for this?
        previousPos = new Vector2f(-1, -1);
        currentPos = new Vector2f();
        displVec = new Vector2f();
        leftButtonPressed = false;
        rightButtonPressed = false;
        inWindow = false;

        glfwSetCursorPosCallback(
                windowHandle,
                (handle, xpos, ypos) -> {
                    currentPos.x = (float) xpos;
                    currentPos.y = (float) ypos;
                });
        glfwSetCursorEnterCallback(windowHandle, (handle, entered) -> inWindow = entered);
        glfwSetMouseButtonCallback(
                windowHandle,
                (handle, button, action, mode) -> {
                    if (button == GLFW_MOUSE_BUTTON_1) {
                        switch (action) {
                            case GLFW_PRESS:
                                leftButtonPressed = true;
                                break;
                            case GLFW_RELEASE:
                                leftButtonPressed = false;
                                break;
                            case GLFW_REPEAT:
                            default:
                                break;
                        }
                    }
                    if (button == GLFW_MOUSE_BUTTON_2) {
                        switch (action) {
                            case GLFW_PRESS:
                                rightButtonPressed = true;
                                glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
                                break;
                            case GLFW_RELEASE:
                                rightButtonPressed = false;
                                glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
                                break;
                            case GLFW_REPEAT:
                            default:
                                break;
                        }
                    }
                });
    }

    /** Calculate input for a window. */
    public void input() {
        displVec.x = 0;
        displVec.y = 0;
        if (previousPos.x > 0 && previousPos.y > 0 && inWindow) {
            double deltax = currentPos.x - previousPos.x;
            double deltay = currentPos.y - previousPos.y;
            boolean rotateX = deltax != 0;
            boolean rotateY = deltay != 0;
            if (rotateX) {
                displVec.y = (float) deltax;
            }
            if (rotateY) {
                displVec.x = (float) deltay;
            }
        }
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
    }
}
