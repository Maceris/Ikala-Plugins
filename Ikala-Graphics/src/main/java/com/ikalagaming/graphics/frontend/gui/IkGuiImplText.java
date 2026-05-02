package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.FontBackup;
import com.ikalagaming.graphics.frontend.gui.data.IkString;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

    public static void popFont() {
        if (context.fontStack.isEmpty()) {
            log.error("Trying to pop a font when none are pushed");
            return;
        }
        FontBackup backupInfo = context.fontStack.pop();

        if (backupInfo.name() == null) {
            context.font = null;
            context.fontSize = backupInfo.size();
            return;
        }

        if (!context.io.fonts.isFontLoaded(backupInfo.name())) {
            log.warn(
                    "The font {} was unloaded while it was on the font stack, ignoring it",
                    backupInfo.name());
            return;
        }
        context.font = context.io.fonts.getFont(backupInfo.name());
        context.fontSize = backupInfo.size();
    }

    public static void pushFont(@NonNull String font, int size) {
        String oldFont = context.font == null ? null : context.font.name;
        context.fontStack.push(new FontBackup(oldFont, context.fontSize));
        context.font = context.io.fonts.getFont(font);
        context.fontSize = size;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplText() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
