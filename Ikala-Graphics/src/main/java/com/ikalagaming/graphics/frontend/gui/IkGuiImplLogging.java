package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;

import lombok.NonNull;

class IkGuiImplLogging {
    static Context context;

    public static void logButtons() {
        // TODO(ches) complete this
    }

    public static void logFinish() {
        // TODO(ches) complete this
    }

    public static void logText(@NonNull String text) {
        // TODO(ches) complete this
    }

    public static void logToClipboard(int maxDepth) {
        // TODO(ches) complete this
    }

    public static void logToFile(int maxDepth, String filename) {
        // TODO(ches) complete this
    }

    public static void logToTTY(int maxDepth) {
        // TODO(ches) complete this
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplLogging() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
