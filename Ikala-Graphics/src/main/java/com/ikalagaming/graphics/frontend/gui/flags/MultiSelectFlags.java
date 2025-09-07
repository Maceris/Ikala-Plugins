package com.ikalagaming.graphics.frontend.gui.flags;

public class MultiSelectFlags {
    public static final int NONE = 0;
    public static final int SINGLE_SELECT = 1;
    public static final int NO_SELECT_ALL = 1 << 1;
    public static final int NO_RANGE_SELECT = 1 << 2;
    public static final int NO_AUTO_SELECT = 1 << 3;
    public static final int NO_AUTO_CLEAR = 1 << 4;
    public static final int NO_AUTO_CLEAR_ON_RESELECT = 1 << 5;
    public static final int BOX_SELECT_1D = 1 << 6;
    public static final int BOX_SELECT_2D = 1 << 7;
    public static final int BOX_SELECT_NO_SCROLL = 1 << 8;
    public static final int CLEAR_ON_ESCAPE = 1 << 9;
    public static final int CLEAR_ON_CLICK_VOID = 1 << 10;
    public static final int SCOPE_WINDOW = 1 << 11;
    public static final int SCOPE_RECT = 1 << 12;
    public static final int SELECT_ON_CLICK = 1 << 13;
    public static final int SELECT_ON_CLICK_RELEASE = 1 << 14;
    public static final int NAV_WRAP_X = 1 << 15;
}
