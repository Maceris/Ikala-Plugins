package com.ikalagaming.graphics.frontend.gui.flags;

public class LogFlags {
    public static final int NONE = 0;
    public static final int OUTPUT_TERMINAL = 0;
    public static final int OUTPUT_FILE = 0;
    public static final int OUTPUT_BUFFER = 0;
    public static final int OUTPUT_CLIPBOARD = 0;

    // Combined flags
    public static final int OUTPUT_MASK =
            OUTPUT_TERMINAL | OUTPUT_FILE | OUTPUT_BUFFER | OUTPUT_CLIPBOARD;
}
