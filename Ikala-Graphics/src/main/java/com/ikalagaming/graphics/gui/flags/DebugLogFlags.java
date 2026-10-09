package com.ikalagaming.graphics.gui.flags;

/**
 * Flags for the debug log, which select which events are recorded and where they are output.
 *
 * @see com.ikalagaming.graphics.gui.IkGui#showDebugLogWindow
 */
public class DebugLogFlags {
    public static final int NONE = 0;

    /** Errors reported by the library, about incorrect API usage. */
    public static final int EVENT_ERROR = 1;

    /** Changes to the active item. */
    public static final int EVENT_ACTIVE_ID = 1 << 1;

    /** Changes to the focused window or item. */
    public static final int EVENT_FOCUS = 1 << 2;

    /** Opening and closing popups. */
    public static final int EVENT_POPUP = 1 << 3;

    /** Keyboard/gamepad navigation requests and results. */
    public static final int EVENT_NAV = 1 << 4;

    /** List clipper steps, which are very spammy. */
    public static final int EVENT_CLIPPER = 1 << 5;

    /** Multi-selection requests. */
    public static final int EVENT_SELECTION = 1 << 6;

    /** Input events from the platform. */
    public static final int EVENT_IO = 1 << 7;

    /** Font loading and caching. */
    public static final int EVENT_FONT = 1 << 8;

    /** Shortcut routing decisions, which are very spammy. */
    public static final int EVENT_INPUT_ROUTING = 1 << 9;

    /** Docking requests and node changes. */
    public static final int EVENT_DOCKING = 1 << 10;

    /** Viewport and platform window changes. */
    public static final int EVENT_VIEWPORT = 1 << 11;

    /** Table storage changes. */
    public static final int EVENT_TABLE = 1 << 12;

    /** All the event types. */
    public static final int EVENT_MASK =
            EVENT_ERROR
                    | EVENT_ACTIVE_ID
                    | EVENT_FOCUS
                    | EVENT_POPUP
                    | EVENT_NAV
                    | EVENT_CLIPPER
                    | EVENT_SELECTION
                    | EVENT_TABLE
                    | EVENT_IO
                    | EVENT_FONT
                    | EVENT_INPUT_ROUTING
                    | EVENT_DOCKING
                    | EVENT_VIEWPORT;

    /** Also send log entries to the regular application logger, at the debug level. */
    public static final int OUTPUT_TO_LOGGER = 1 << 20;

    /** Private constructor so this is not instantiated. */
    private DebugLogFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
