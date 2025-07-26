package com.ikalagaming.graphics.frontend.gui.flags;

public class TableFlags {
    public static final int NONE = 0;
    public static final int RESIZABLE = 1;
    public static final int REORDERABLE = 1 << 1;
    public static final int HIDEABLE = 1 << 2;
    public static final int SORTABLE = 1 << 3;
    public static final int NO_SAVED_SETTINGS = 1 << 4;
    public static final int CONTEXT_MENU_IN_BODY = 1 << 5;
    public static final int ROW_BACKGROUND = 1 << 6;
    public static final int BORDERS_INNER_H = 1 << 7;
    public static final int BORDERS_OUTER_H = 1 << 8;
    public static final int BORDERS_INNER_V = 1 << 9;
    public static final int BORDERS_OUTER_V = 1 << 10;
    public static final int NO_BORDERS_IN_BODY = 1 << 11;
    public static final int NO_BORDERS_IN_BODY_UNTIL_RESIZE = 1 << 12;
    public static final int SIZING_FIXED_FIT = 1 << 13;
    public static final int SIZING_FIXED_SAME = 1 << 14;
    public static final int SIZING_STRETCH_SAME = 1 << 15;
    public static final int NO_HOST_EXTEND_X = 1 << 16;
    public static final int NO_HOST_EXTEND_Y = 1 << 17;
    public static final int NO_KEEP_COLUMNS_VISIBLE = 1 << 18;
    public static final int PRECISE_WIDTHS = 1 << 19;
    public static final int NO_CLIP = 1 << 20;
    public static final int PAD_OUTER_X = 1 << 21;
    public static final int NO_PAD_OUTER_X = 1 << 22;
    public static final int NO_PAD_INNER_X = 1 << 23;
    public static final int SCROLL_X = 1 << 24;
    public static final int SCROLL_Y = 1 << 25;
    public static final int SORT_MULTI = 1 << 26;
    public static final int SORT_TRISTATE = 1 << 27;

    // Combined flags
    public static final int BORDERS_H = BORDERS_OUTER_H | BORDERS_INNER_H;
    public static final int BORDERS_V = BORDERS_INNER_V | BORDERS_OUTER_V;
    public static final int BORDERS_INNER = BORDERS_INNER_H | BORDERS_INNER_V;
    public static final int BORDERS_OUTER = BORDERS_OUTER_H | BORDERS_OUTER_V;
    public static final int BORDERS = BORDERS_INNER | BORDERS_OUTER;
    public static final int SIZING_STRETCH_PROP = SIZING_FIXED_FIT | SIZING_FIXED_SAME;
}
