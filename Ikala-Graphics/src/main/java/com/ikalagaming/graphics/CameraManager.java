package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.scene.Camera;

import lombok.Getter;
import lombok.NonNull;

/** Handles the position and movement of the camera. */
public class CameraManager {

    /** Radians per pixel of mouse movement. */
    private static final float DEFAULT_MOUSE_SENSITIVITY = calculateSensitivity(40, 400);

    /**
     * Calculate the sensitivity unit to use.
     *
     * @param cmPerTurn The number of centimeters a mouse should travel to complete a 360-degree
     *     turn.
     * @param dpi The dpi of the mouse moving said distance.
     * @return The radians per pixel of mouse movement that would result in the provided cmPerTurn
     *     at the given dpi.
     */
    private static float calculateSensitivity(int cmPerTurn, int dpi) {
        final float pxPerCm = dpi / 2.54f;
        final double totalRotation = Math.toRadians(360);
        final double result = totalRotation / (cmPerTurn * pxPerCm);
        return (float) result;
    }

    /** The speed in world units that the camera moves per second. */
    private static final float MOVE_SPEED_PER_SECOND = 5f;

    /**
     * The camera for the window.
     *
     * @return The camera.
     */
    @Getter private final Camera camera;

    /** The window we are managing the camera for. */
    private final Window window;

    /** The mouse sensitivity. Radians per pixel of mouse movement. */
    private float sensitivity;

    /**
     * Create a new camera manager for the given window.
     *
     * @param camera The camera we are managing.
     * @param window The window the camera renders to.
     */
    public CameraManager(@NonNull Camera camera, @NonNull Window window) {
        this.camera = camera;
        this.window = window;
        this.sensitivity = DEFAULT_MOUSE_SENSITIVITY;
    }

    /**
     * Set the sensitivity unit to use.
     *
     * @param cmPerTurn The number of centimeters a mouse should travel to complete a 360-degree
     *     turn.
     * @param dpi The dpi of the mouse moving said distance.
     */
    public void setSensitivity(int cmPerTurn, int dpi) {
        sensitivity = calculateSensitivity(cmPerTurn, dpi);
    }

    /**
     * Actually reposition the camera based on how much time has passed.
     *
     * @param deltaTime The fractional part of a second since the last update.
     */
    public void updateCamera(float deltaTime) {
        if (window.isKeyPressed(GLFW_KEY_W)) {
            camera.moveForward(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
        }
        if (window.isKeyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
        }
        if (window.isKeyPressed(GLFW_KEY_A)) {
            camera.moveLeft(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
        }
        if (window.isKeyPressed(GLFW_KEY_D)) {
            camera.moveRight(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
        }
        if (window.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            camera.moveDown(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
        }
        if (window.isKeyPressed(GLFW_KEY_SPACE)) {
            camera.moveUp(CameraManager.MOVE_SPEED_PER_SECOND * deltaTime);
        }

        // Update camera based on mouse
        IkIO ikIO = IkGui.getIO();
        if (!ikIO.wantCaptureMouse && ikIO.getMouseDown(MouseButton.RIGHT)) {
            float deltaX = ikIO.mouseDelta.x * sensitivity;
            float deltaY = ikIO.mouseDelta.y * sensitivity;
            camera.addRotation(deltaY, deltaX);
        }
    }
}
