package com.ikalagaming.graphics.frontend.gui.flags;

public class TableColumnFlags {
    public static final int NONE = 0;
    public static final int DISABLED = 1;
    public static final int DEFAULT_HIDE = 1 << 1;
    public static final int DEFAULT_SORT = 1 << 2;
    public static final int WIDTH_STRETCH = 1 << 3;
    public static final int WIDTH_FIXED = 1 << 4;
    public static final int NO_RESIZE = 1 << 5;
    public static final int NO_REORDER = 1 << 6;
    public static final int NO_HIDE = 1 << 7;
    public static final int NO_CLIP = 1 << 8;
    public static final int NO_SORT = 1 << 9;
    public static final int NO_SORT_ASCENDING = 1 << 10;
    public static final int NO_SORT_DESCENDING = 1 << 11;
    public static final int NO_HEADER_LABEL = 1 << 12;
    public static final int NO_HEADER_WIDTH = 1 << 13;
    public static final int PREFER_SORT_ASCENDING = 1 << 14;
    public static final int PREFER_SORT_DESCENDING = 1 << 15;
    public static final int INDENT_ENABLE = 1 << 16;
    public static final int INDENT_DISABLE = 1 << 17;
    public static final int IS_ENABLED = 1 << 18;
    public static final int IS_VISIBLE = 1 << 19;
    public static final int IS_SORTED = 1 << 20;
    public static final int IS_HOVERED = 1 << 21;
}
