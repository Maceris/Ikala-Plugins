package com.ikalagaming.graphics.frontend.gui.flags;

public class DebugLogFlags {
    public static final int NONE = 0;
    public static final int EVENT_ERROR = 0;
    public static final int EVENT_ACTIVE_ID = 0;
    public static final int EVENT_FOCUS = 0;
    public static final int EVENT_POPUP = 0;
    public static final int EVENT_NAV = 0;
    public static final int EVENT_CLIPPER = 0;
    public static final int EVENT_SELECTION = 0;
    public static final int EVENT_IO = 0;
    public static final int EVENT_FONT = 0;
    public static final int EVENT_INPUT_ROUTING = 0;
    public static final int EVENT_DOCKING = 0;
    public static final int EVENT_VIEWPORT = 0;

    // Combined flags
    public static final int EVENT_MASK =
            EVENT_ERROR
                    | EVENT_ACTIVE_ID
                    | EVENT_FOCUS
                    | EVENT_POPUP
                    | EVENT_NAV
                    | EVENT_CLIPPER
                    | EVENT_SELECTION
                    | EVENT_IO
                    | EVENT_FONT
                    | EVENT_INPUT_ROUTING
                    | EVENT_DOCKING
                    | EVENT_VIEWPORT;

    /** Private constructor so this is not instantiated. */
    private DebugLogFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
