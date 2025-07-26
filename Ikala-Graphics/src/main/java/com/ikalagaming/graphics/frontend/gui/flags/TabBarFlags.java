package com.ikalagaming.graphics.frontend.gui.flags;

public class TabBarFlags {
    public static final int NONE = 0;
    public static final int REORDERABLE = 1;
    public static final int AUTO_SELECT_NEW_TABS = 1 << 1;
    public static final int TAB_LIST_POPUP_BUTTON = 1 << 2;
    public static final int NO_CLOSE_WITH_MIDDLE_MOUSE_BUTTON = 1 << 3;
    public static final int NO_TAB_LIST_SCROLLING_BUTTONS = 1 << 4;
    public static final int NO_TOOLTIP = 1 << 5;
    public static final int FITTING_POLICY_RESIZE_DOWN = 1 << 6;
    public static final int FITTING_POLICY_SCROLL = 1 << 7;

    // Combined flags
    public static final int FITTING_POLICY_DEFAULT = FITTING_POLICY_RESIZE_DOWN;
    public static final int FITTING_POLICY_MASK =
            FITTING_POLICY_RESIZE_DOWN | FITTING_POLICY_SCROLL;
}
