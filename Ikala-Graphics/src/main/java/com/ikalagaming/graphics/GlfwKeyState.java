package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.input.KeyState;

import lombok.NonNull;

import java.util.Arrays;

/**
 * The keys held in the scene window, for {@link com.ikalagaming.graphics.input.Controls}. Keyboard
 * keys come from GLFW through {@link Window#isKeyPressed}, so keys a script holds count and a text
 * field taking the keyboard hides them; mouse buttons come from IkGui, hidden while the GUI has the
 * mouse. Main thread only.
 */
final class GlfwKeyState implements KeyState {

    /** The GLFW key code for each IkGui key, by ordinal, or -1 for none. */
    private static final int[] GLFW_CODES = glfwCodes();

    @Override
    public boolean isDown(@NonNull Key key) {
        final Window window = GraphicsManager.getSceneWindow();
        if (window == null) {
            return false;
        }
        return switch (key) {
            case MOD_CTRL ->
                    window.isKeyPressed(GLFW_KEY_LEFT_CONTROL)
                            || window.isKeyPressed(GLFW_KEY_RIGHT_CONTROL);
            case MOD_SHIFT ->
                    window.isKeyPressed(GLFW_KEY_LEFT_SHIFT)
                            || window.isKeyPressed(GLFW_KEY_RIGHT_SHIFT);
            case MOD_ALT ->
                    window.isKeyPressed(GLFW_KEY_LEFT_ALT)
                            || window.isKeyPressed(GLFW_KEY_RIGHT_ALT);
            case MOUSE_LEFT -> mouseDown(MouseButton.LEFT);
            case MOUSE_RIGHT -> mouseDown(MouseButton.RIGHT);
            case MOUSE_MIDDLE -> mouseDown(MouseButton.MIDDLE);
            case MOUSE_BACK -> mouseDown(MouseButton.BACK);
            case MOUSE_FORWARD -> mouseDown(MouseButton.FORWARD);
            default -> {
                final int code = GLFW_CODES[key.ordinal()];
                yield code >= 0 && window.isKeyPressed(code);
            }
        };
    }

    @Override
    public boolean keyboardBlocked() {
        return IkGui.getContext() != null && IkGui.getIO().wantCaptureKeyboard;
    }

    /**
     * Whether a mouse button is down in the scene, not over the GUI.
     *
     * @param button The button.
     * @return True if it is down.
     */
    private static boolean mouseDown(@NonNull MouseButton button) {
        if (IkGui.getContext() == null) {
            return false;
        }
        final IkIO io = IkGui.getIO();
        if ((io.configFlags & ConfigFlags.NO_MOUSE) != 0) {
            // Mouse look: the GUI ignores the mouse, so ask GLFW
            final Window window = GraphicsManager.getSceneWindow();
            return window != null && window.isMouseButtonPressed(button.index);
        }
        return !io.wantCaptureMouse && io.getMouseDown(button);
    }

    /**
     * Invert the GLFW to IkGui key mapping.
     *
     * @return The GLFW code for each IkGui key, by ordinal.
     */
    private static int[] glfwCodes() {
        final int[] codes = new int[Key.COUNT];
        Arrays.fill(codes, -1);
        for (int code = GLFW_KEY_SPACE; code <= GLFW_KEY_LAST; ++code) {
            final Key key = GlfwGuiBackend.mapGLFWToIkGuiKey(code);
            if (key != Key.NONE && codes[key.ordinal()] < 0) {
                codes[key.ordinal()] = code;
            }
        }
        return codes;
    }
}
