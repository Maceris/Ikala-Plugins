package com.ikalagaming.graphics.frontend.gui.flags;

public class ButtonFlags {
    public static final int NONE = 0;
    public static final int MOUSE_BUTTON_LEFT = 1;
    public static final int MOUSE_BUTTON_RIGHT = 1 << 1;
    public static final int MOUSE_BUTTON_MIDDLE = 1 << 2;

    /** Private constructor so this is not instantiated. */
    private ButtonFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
