package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;

import com.ikalagaming.graphics.gui.enums.Key;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

class GamepadInputTest {

    private record State(boolean down, float value) {}

    private static Map<Key, State> apply(byte[] buttons, float[] axes) {
        Map<Key, State> keys = new EnumMap<>(Key.class);
        GamepadInput.apply(
                buttons, axes, (key, down, value) -> keys.put(key, new State(down, value)));
        return keys;
    }

    private static byte[] buttons() {
        return new byte[GLFW_GAMEPAD_BUTTON_LAST + 1];
    }

    /** Sticks centered and triggers released. */
    private static float[] restingAxes() {
        float[] axes = new float[GLFW_GAMEPAD_AXIS_LAST + 1];
        axes[GLFW_GAMEPAD_AXIS_LEFT_TRIGGER] = -1;
        axes[GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] = -1;
        return axes;
    }

    @Test
    void buttonsMapToTheirKeys() {
        byte[] buttons = buttons();
        buttons[GLFW_GAMEPAD_BUTTON_A] = GLFW_PRESS;
        buttons[GLFW_GAMEPAD_BUTTON_DPAD_DOWN] = GLFW_PRESS;
        buttons[GLFW_GAMEPAD_BUTTON_LEFT_THUMB] = GLFW_PRESS;

        Map<Key, State> keys = apply(buttons, restingAxes());

        assertTrue(keys.get(Key.GAMEPAD_FACE_DOWN).down());
        assertTrue(keys.get(Key.GAMEPAD_DPAD_DOWN).down());
        assertTrue(keys.get(Key.GAMEPAD_L3).down());
        assertFalse(keys.get(Key.GAMEPAD_FACE_RIGHT).down());
        assertFalse(keys.get(Key.GAMEPAD_START).down());
    }

    @Test
    void sticksHaveADeadZone() {
        float[] axes = restingAxes();
        axes[GLFW_GAMEPAD_AXIS_LEFT_X] = 0.2f;
        axes[GLFW_GAMEPAD_AXIS_LEFT_Y] = -1f;

        Map<Key, State> keys = apply(buttons(), axes);

        assertEquals(0, keys.get(Key.GAMEPAD_LSTICK_RIGHT).value(), 1e-6, "Inside the dead zone");
        assertFalse(keys.get(Key.GAMEPAD_LSTICK_RIGHT).down());
        assertEquals(1, keys.get(Key.GAMEPAD_LSTICK_UP).value(), 1e-6, "Pushed all the way up");
        assertTrue(keys.get(Key.GAMEPAD_LSTICK_UP).down());
        assertEquals(0, keys.get(Key.GAMEPAD_LSTICK_DOWN).value(), 1e-6);
    }

    @Test
    void triggersMapFromRestToFullyPressed() {
        float[] axes = restingAxes();
        Map<Key, State> resting = apply(buttons(), axes);
        assertEquals(0, resting.get(Key.GAMEPAD_L2).value(), 1e-6);
        assertFalse(resting.get(Key.GAMEPAD_L2).down());

        axes[GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] = 1;
        axes[GLFW_GAMEPAD_AXIS_LEFT_TRIGGER] = GamepadInput.TRIGGER_REST;
        Map<Key, State> pressed = apply(buttons(), axes);
        assertEquals(1, pressed.get(Key.GAMEPAD_R2).value(), 1e-6);
        assertTrue(pressed.get(Key.GAMEPAD_R2).down());
        assertEquals(0, pressed.get(Key.GAMEPAD_L2).value(), 1e-6);
    }
}
