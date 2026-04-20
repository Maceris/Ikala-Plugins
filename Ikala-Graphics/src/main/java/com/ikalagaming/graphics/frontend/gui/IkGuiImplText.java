package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.IkString;

import lombok.NonNull;

class IkGuiImplText {
    static Context context;

    public static void alignTextToFramePadding() {
        // TODO(ches) complete this
    }

    public static void bullet() {
        // TODO(ches) complete this
    }

    public static void bulletText(String text) {
        // TODO(ches) complete this
    }

    public static boolean inputText(
            String label,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return false;
    }

    public static boolean inputTextMultiline(
            String label,
            @NonNull IkString text,
            float width,
            float height,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return false;
    }

    public static boolean inputTextWithHint(
            String label,
            String hint,
            @NonNull IkString text,
            int inputTextFlags,
            GuiInputTextCallback callback) {
        return false;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplText() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
