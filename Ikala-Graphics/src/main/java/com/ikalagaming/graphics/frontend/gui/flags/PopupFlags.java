package com.ikalagaming.graphics.frontend.gui.flags;

public class PopupFlags {
    public static final int NONE = 0;
    public static final int MOUSE_BUTTON_LEFT = 1;
    public static final int MOUSE_BUTTON_RIGHT = 1 << 1;
    public static final int MOUSE_BUTTON_MIDDLE = 1 << 2;
    public static final int NO_OPEN_OVER_EXISTING_POPUP = 1 << 3;
    public static final int NO_OPEN_OVER_ITEMS = 1 << 4;
    public static final int ANY_POPUP_ID = 1 << 5;
    public static final int ANY_POPUP_LEVEL = 1 << 6;

    // Combined flags
    public static final int MOUSE_BUTTON_DEFAULT = MOUSE_BUTTON_LEFT;
    public static final int MOUSE_BUTTON_MASK =
            MOUSE_BUTTON_LEFT | MOUSE_BUTTON_RIGHT | MOUSE_BUTTON_MIDDLE;
    public static final int ANY_POPUP = ANY_POPUP_ID | ANY_POPUP_LEVEL;
}
