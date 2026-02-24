package com.ikalagaming.graphics.frontend.gui.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MouseButton {
    NONE(-1),
    LEFT(0),
    RIGHT(1),
    MIDDLE(2),
    BACK(3),
    FORWARD(4);

    /**
     * The index of the mouse button. -1 for NONE, 0 is LEFT, 1 is RIGHT, 2 is MIDDLE, 3 is BACK,
     * and 4 is FORWARD.
     */
    public final int index;

    /** The number of mouse buttons we support. Not a valid enum value. */
    public static final int COUNT = 5;
}
