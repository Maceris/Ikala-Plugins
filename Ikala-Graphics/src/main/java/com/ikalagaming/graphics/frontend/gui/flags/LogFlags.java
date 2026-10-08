package com.ikalagaming.graphics.frontend.gui.flags;

/** Flags for logging, only one output type may be used at a time. */
public class LogFlags {
    public static final int NONE = 0;

    /** Log to the terminal (standard output). */
    public static final int OUTPUT_TERMINAL = 1;

    /** Log to a file. */
    public static final int OUTPUT_FILE = 1 << 1;

    /** Log to the log buffer in the context. */
    public static final int OUTPUT_BUFFER = 1 << 2;

    /** Log to the clipboard when logging finishes. */
    public static final int OUTPUT_CLIPBOARD = 1 << 3;

    // Combined flags
    public static final int OUTPUT_MASK =
            OUTPUT_TERMINAL | OUTPUT_FILE | OUTPUT_BUFFER | OUTPUT_CLIPBOARD;

    /** Private constructor so this is not instantiated. */
    private LogFlags() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
