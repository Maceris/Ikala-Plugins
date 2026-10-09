package com.ikalagaming.graphics;

import static org.lwjgl.glfw.GLFW.*;

import com.ikalagaming.graphics.gui.enums.Key;

import lombok.NonNull;

/**
 * Turns a GLFW gamepad's state into IkGui key events, the same way Dear ImGui's GLFW backend does.
 * Buttons are digital, while sticks and triggers become analog values between 0 and 1.
 */
final class GamepadInput {

    /** Receives a key event. */
    @FunctionalInterface
    interface KeySink {
        /**
         * Handle a key.
         *
         * @param key The key.
         * @param down Whether it is pressed.
         * @param value How far it is pressed, from 0 to 1.
         */
        void key(@NonNull Key key, boolean down, float value);
    }

    /** How far a stick has to move before it counts. */
    static final float STICK_DEAD_ZONE = 0.25f;

    /** The resting position of a trigger axis, which GLFW reports from -1 when released. */
    static final float TRIGGER_REST = -0.75f;

    /**
     * Send the state of every gamepad input as key events.
     *
     * @param buttons The GLFW button states, indexed by {@code GLFW_GAMEPAD_BUTTON_*}.
     * @param axes The GLFW axis values, indexed by {@code GLFW_GAMEPAD_AXIS_*}.
     * @param sink Receives the key events.
     */
    static void apply(byte[] buttons, float[] axes, @NonNull KeySink sink) {
        button(sink, buttons, Key.GAMEPAD_START, GLFW_GAMEPAD_BUTTON_START);
        button(sink, buttons, Key.GAMEPAD_BACK, GLFW_GAMEPAD_BUTTON_BACK);
        // Xbox X, PlayStation square
        button(sink, buttons, Key.GAMEPAD_FACE_LEFT, GLFW_GAMEPAD_BUTTON_X);
        // Xbox B, PlayStation circle
        button(sink, buttons, Key.GAMEPAD_FACE_RIGHT, GLFW_GAMEPAD_BUTTON_B);
        // Xbox Y, PlayStation triangle
        button(sink, buttons, Key.GAMEPAD_FACE_UP, GLFW_GAMEPAD_BUTTON_Y);
        // Xbox A, PlayStation cross
        button(sink, buttons, Key.GAMEPAD_FACE_DOWN, GLFW_GAMEPAD_BUTTON_A);
        button(sink, buttons, Key.GAMEPAD_DPAD_LEFT, GLFW_GAMEPAD_BUTTON_DPAD_LEFT);
        button(sink, buttons, Key.GAMEPAD_DPAD_RIGHT, GLFW_GAMEPAD_BUTTON_DPAD_RIGHT);
        button(sink, buttons, Key.GAMEPAD_DPAD_UP, GLFW_GAMEPAD_BUTTON_DPAD_UP);
        button(sink, buttons, Key.GAMEPAD_DPAD_DOWN, GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
        button(sink, buttons, Key.GAMEPAD_L1, GLFW_GAMEPAD_BUTTON_LEFT_BUMPER);
        button(sink, buttons, Key.GAMEPAD_R1, GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER);
        button(sink, buttons, Key.GAMEPAD_L3, GLFW_GAMEPAD_BUTTON_LEFT_THUMB);
        button(sink, buttons, Key.GAMEPAD_R3, GLFW_GAMEPAD_BUTTON_RIGHT_THUMB);

        analog(sink, axes, Key.GAMEPAD_L2, GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, TRIGGER_REST, 1.0f);
        analog(sink, axes, Key.GAMEPAD_R2, GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, TRIGGER_REST, 1.0f);
        stick(
                sink,
                axes,
                Key.GAMEPAD_LSTICK_LEFT,
                Key.GAMEPAD_LSTICK_RIGHT,
                GLFW_GAMEPAD_AXIS_LEFT_X);
        stick(sink, axes, Key.GAMEPAD_LSTICK_UP, Key.GAMEPAD_LSTICK_DOWN, GLFW_GAMEPAD_AXIS_LEFT_Y);
        stick(
                sink,
                axes,
                Key.GAMEPAD_RSTICK_LEFT,
                Key.GAMEPAD_RSTICK_RIGHT,
                GLFW_GAMEPAD_AXIS_RIGHT_X);
        stick(
                sink,
                axes,
                Key.GAMEPAD_RSTICK_UP,
                Key.GAMEPAD_RSTICK_DOWN,
                GLFW_GAMEPAD_AXIS_RIGHT_Y);
    }

    /**
     * Send a button.
     *
     * @param sink Receives the key event.
     * @param buttons The button states.
     * @param key The IkGui key.
     * @param button The GLFW button index.
     */
    private static void button(KeySink sink, byte[] buttons, Key key, int button) {
        boolean down = buttons[button] == GLFW_PRESS;
        sink.key(key, down, down ? 1.0f : 0.0f);
    }

    /**
     * Send both directions of a stick axis. Negative values are left or up.
     *
     * @param sink Receives the key events.
     * @param axes The axis values.
     * @param negative The key for the negative direction.
     * @param positive The key for the positive direction.
     * @param axis The GLFW axis index.
     */
    private static void stick(KeySink sink, float[] axes, Key negative, Key positive, int axis) {
        analog(sink, axes, negative, axis, -STICK_DEAD_ZONE, -1.0f);
        analog(sink, axes, positive, axis, STICK_DEAD_ZONE, 1.0f);
    }

    /**
     * Send an axis as an analog key, mapping one range of the axis onto 0 to 1.
     *
     * @param sink Receives the key event.
     * @param axes The axis values.
     * @param key The IkGui key.
     * @param axis The GLFW axis index.
     * @param from The axis value that maps to 0.
     * @param to The axis value that maps to 1.
     */
    private static void analog(
            KeySink sink, float[] axes, Key key, int axis, float from, float to) {
        float value = (axes[axis] - from) / (to - from);
        value = Math.clamp(value, 0.0f, 1.0f);
        sink.key(key, value > 0.1f, value);
    }

    /** Static mapping only. */
    private GamepadInput() {}
}
