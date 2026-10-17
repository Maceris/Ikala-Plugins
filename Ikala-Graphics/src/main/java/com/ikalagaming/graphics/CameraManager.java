package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.scene.Camera;

import lombok.Getter;
import lombok.NonNull;

import java.util.function.Supplier;

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

    /** How much faster the camera moves in fast mode. */
    private static final float FAST_MULTIPLIER = 10f;

    /** How to move the camera, for tools to show. */
    public static final String CONTROLS =
            "Hold the right mouse button to turn; W, A, S, D, Space and Shift to move; Ctrl"
                    + " switches to 10x speed and back.";

    /**
     * The camera for the window.
     *
     * @return The camera.
     */
    @Getter private final Camera camera;

    /** The window the scene is shown in, whose keys move the camera. */
    private final Supplier<Window> window;

    /** The mouse sensitivity. Radians per pixel of mouse movement. */
    private float sensitivity;

    /** Whether the right mouse button was turning the camera the last time it updated. */
    private boolean wasTurning;

    /**
     * Whether the camera moves at fast speed. Pressing either Ctrl key toggles it.
     *
     * @return True if moving fast.
     */
    @Getter private boolean fast;

    /** Whether a Ctrl key was down the last time the camera updated. */
    private boolean wasControl;

    /**
     * Create a new camera manager, moved by the keys of whichever window shows the scene.
     *
     * @param camera The camera we are managing.
     * @param window Fetches the window the scene is shown in, which can change.
     */
    public CameraManager(@NonNull Camera camera, @NonNull Supplier<Window> window) {
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
        IkIO ikIO = IkGui.getIO();
        final boolean turning = !ikIO.wantCaptureMouse && ikIO.getMouseDown(MouseButton.RIGHT);
        if (turning && !wasTurning) {
            /*
             * Like Dear ImGui, IkGui only drops window focus for a left click in empty space, so
             * the last window used would keep the keyboard and the movement keys would do nothing.
             * Popups and modals capture the mouse, so we never get here with one open.
             */
            IkGui.setWindowFocus(null);
        }
        wasTurning = turning;

        final Window window = this.window.get();
        // Toggle on the press, not while held; not while typing, where Ctrl makes shortcuts
        final boolean control =
                window.isKeyPressed(GLFW_KEY_LEFT_CONTROL)
                        || window.isKeyPressed(GLFW_KEY_RIGHT_CONTROL);
        if (control && !wasControl && !ikIO.wantCaptureKeyboard) {
            fast = !fast;
        }
        wasControl = control;

        final float step = MOVE_SPEED_PER_SECOND * (fast ? FAST_MULTIPLIER : 1) * deltaTime;
        if (window.isKeyPressed(GLFW_KEY_W)) {
            camera.moveForward(step);
        }
        if (window.isKeyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(step);
        }
        if (window.isKeyPressed(GLFW_KEY_A)) {
            camera.moveLeft(step);
        }
        if (window.isKeyPressed(GLFW_KEY_D)) {
            camera.moveRight(step);
        }
        if (window.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            camera.moveDown(step);
        }
        if (window.isKeyPressed(GLFW_KEY_SPACE)) {
            camera.moveUp(step);
        }

        // Update camera based on mouse
        if (turning) {
            float deltaX = ikIO.mouseDelta.x * sensitivity;
            float deltaY = ikIO.mouseDelta.y * sensitivity;
            camera.addRotation(deltaY, deltaX);
        }
    }
}
