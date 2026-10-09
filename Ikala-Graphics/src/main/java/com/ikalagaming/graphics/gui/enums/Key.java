package com.ikalagaming.graphics.gui.enums;

import lombok.NonNull;

public enum Key {
    NONE,
    // Letters
    A,
    B,
    C,
    D,
    E,
    F,
    G,
    H,
    I,
    J,
    K,
    L,
    M,
    N,
    O,
    P,
    Q,
    R,
    S,
    T,
    U,
    V,
    W,
    X,
    Y,
    Z,
    // Numbers
    ZERO,
    ONE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    // Numpad
    NUMPAD_ZERO,
    NUMPAD_ONE,
    NUMPAD_TWO,
    NUMPAD_THREE,
    NUMPAD_FOUR,
    NUMPAD_FIVE,
    NUMPAD_SIX,
    NUMPAD_SEVEN,
    NUMPAD_EIGHT,
    NUMPAD_NINE,
    NUM_LOCK,
    NUMPAD_ADD,
    NUMPAD_DIVIDE,
    NUMPAD_ENTER,
    NUMPAD_EQUAL,
    NUMPAD_MULTIPLY,
    NUMPAD_PERIOD,
    NUMPAD_SUBTRACT,
    // Symbols
    AMPERSAND,
    APOSTROPHE,
    ASTERISK,
    AT,
    BACK_SLASH,
    CARET,
    COLON,
    COMMA,
    DOLLAR,
    DOUBLE_QUOTE,
    EQUALS,
    EURO,
    EXCLAMATION_MARK,
    FORWARD_SLASH,
    GRAVE_ACCENT,
    GREATER_THAN,
    LEFT_BRACE,
    LEFT_BRACKET,
    LEFT_PARENTHESES,
    LESS_THAN,
    MINUS,
    NUMBER_SIGN,
    // The key between left shift and Z on non-US keyboards, '<>' or '\|'
    OEM_102,
    PERCENT,
    PERIOD,
    PIPE,
    PLUS,
    POUND,
    QUESTION_MARK,
    RIGHT_BRACE,
    RIGHT_BRACKET,
    RIGHT_PARENTHESES,
    SEMICOLON,
    SINGLE_QUOTE,
    UNDERSCORE,
    // Special keys
    ARROW_DOWN,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    BACKSPACE,
    CAPS_LOCK,
    DELETE,
    END,
    ENTER,
    ESCAPE,
    HOME,
    INSERT,
    LEFT_ALT,
    LEFT_CTRL,
    LEFT_SHIFT,
    LEFT_SUPER,
    MENU,
    PAUSE,
    PAGE_DOWN,
    PAGE_UP,
    PRINT_SCREEN,
    RIGHT_ALT,
    RIGHT_CTRL,
    RIGHT_SHIFT,
    RIGHT_SUPER,
    SCROLL_LOCK,
    SPACE,
    TAB,
    // Function keys
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
    F13,
    F14,
    F15,
    F16,
    F17,
    F18,
    F19,
    F20,
    F21,
    F22,
    F23,
    F24,
    // Gamepad
    GAMEPAD_BACK,
    GAMEPAD_DPAD_DOWN,
    GAMEPAD_DPAD_LEFT,
    GAMEPAD_DPAD_RIGHT,
    GAMEPAD_DPAD_UP,
    GAMEPAD_FACE_DOWN,
    GAMEPAD_FACE_LEFT,
    GAMEPAD_FACE_RIGHT,
    GAMEPAD_FACE_UP,
    GAMEPAD_L1,
    GAMEPAD_L2,
    GAMEPAD_L3,
    GAMEPAD_LSTICK_BUTTON,
    GAMEPAD_LSTICK_DOWN,
    GAMEPAD_LSTICK_LEFT,
    GAMEPAD_LSTICK_RIGHT,
    GAMEPAD_LSTICK_UP,
    GAMEPAD_R1,
    GAMEPAD_R2,
    GAMEPAD_R3,
    GAMEPAD_RSTICK_BUTTON,
    GAMEPAD_RSTICK_DOWN,
    GAMEPAD_RSTICK_LEFT,
    GAMEPAD_RSTICK_RIGHT,
    GAMEPAD_RSTICK_UP,
    GAMEPAD_START,
    // Mouse buttons and wheel, which are aliases of the mouse state. They are set from the mouse
    // events every frame, so backends must not submit them. They can be used with key ownership
    // and the key functions, e.g. setItemKeyOwner(Key.MOUSE_WHEEL_Y) to stop the wheel from
    // scrolling the window while an item is hovered.
    MOUSE_LEFT,
    MOUSE_RIGHT,
    MOUSE_MIDDLE,
    MOUSE_BACK,
    MOUSE_FORWARD,
    MOUSE_WHEEL_X,
    MOUSE_WHEEL_Y,
    // Storage for the merged state of modifiers (either left or right key held). These are used
    // for key ownership of modifiers, and shortcuts that are only a modifier.
    MOD_CTRL,
    MOD_SHIFT,
    MOD_ALT,
    MOD_SUPER;

    /**
     * Whether this is a keyboard key, as opposed to a gamepad key, a mouse key or one of the merged
     * modifier keys.
     *
     * @return True if this is a keyboard key.
     */
    public boolean isKeyboardKey() {
        return this != NONE && ordinal() < GAMEPAD_BACK.ordinal();
    }

    /**
     * Whether this is a gamepad key.
     *
     * @return True if this is a gamepad key.
     */
    public boolean isGamepadKey() {
        return ordinal() >= GAMEPAD_BACK.ordinal() && ordinal() <= GAMEPAD_START.ordinal();
    }

    /**
     * Whether this is one of the mouse keys, which are aliases of the mouse buttons and wheel.
     *
     * @return True if this is a mouse key.
     */
    public boolean isMouseKey() {
        return ordinal() >= MOUSE_LEFT.ordinal() && ordinal() <= MOUSE_WHEEL_Y.ordinal();
    }

    /**
     * The mouse key for a mouse button.
     *
     * @param button The mouse button, which must not be NONE.
     * @return The key for the button.
     */
    public static Key fromMouseButton(@NonNull MouseButton button) {
        return switch (button) {
            case LEFT -> MOUSE_LEFT;
            case RIGHT -> MOUSE_RIGHT;
            case MIDDLE -> MOUSE_MIDDLE;
            case BACK -> MOUSE_BACK;
            case FORWARD -> MOUSE_FORWARD;
            case NONE -> throw new IllegalArgumentException("There is no key for MouseButton.NONE");
        };
    }

    /**
     * Whether this is one of the merged modifier keys (MOD_CTRL etc).
     *
     * @return True if this is a merged modifier key.
     */
    public boolean isModKey() {
        return ordinal() >= MOD_CTRL.ordinal();
    }
}
