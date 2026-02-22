package com.ikalagaming.graphics.frontend.gui.flags;

public class DockNodeFlags {
    public static final int NONE = 0;
    public static final int KEEP_ALIVE_ONLY = 1;
    public static final int NO_CENTRAL_NODE = 1 << 1;
    public static final int NO_DOCKING_IN_CENTRAL_NODE = 1 << 2;
    public static final int PASSTHROUGH_CENTRAL_NODE = 1 << 3;
    public static final int NO_SPLIT = 1 << 4;
    public static final int NO_RESIZE = 1 << 5;
    public static final int AUTO_HIDE_TAB_BAR = 1 << 6;

    /** Private constructor so this is not instantiated. */
    private DockNodeFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
