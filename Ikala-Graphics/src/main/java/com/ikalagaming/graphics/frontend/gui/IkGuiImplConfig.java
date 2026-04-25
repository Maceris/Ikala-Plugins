package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;

import lombok.NonNull;

class IkGuiImplConfig {

    static Context context;

    public static void loadIniSettingsFromDisk(@NonNull String filename) {
        // TODO(ches) complete this
    }

    public static void loadIniSettingsFromMemory(@NonNull String data) {
        // TODO(ches) complete this
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplConfig() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
