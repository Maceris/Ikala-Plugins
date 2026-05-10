package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.callback.GuiInputTextCallback;
import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.FontBackup;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

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

    public static void setFont(@NonNull String fontPath, int size) {
        if (!context.io.fonts.isFontLoaded(fontPath)) {
            log.error(
                    "Font {} is not loaded, cannot use it as the current font until loaded.",
                    fontPath);
            return;
        }
        context.font = context.io.fonts.getFont(fontPath);
        context.fontSize = size;
    }

    public static void setFontFallbacks(@NonNull String... fontList) {
        context.fontFallbacks.clear();
        for (String fontName : fontList) {
            if (!context.io.fonts.isFontLoaded(fontName) && !context.io.fonts.loadFont(fontName)) {
                continue;
            }
            context.fontFallbacks.add(context.io.fonts.getFont(fontName));
        }
    }

    public static void setFontSize(int fontSize) {
        context.fontSize = fontSize;
    }

    public static void text(@NonNull String text) {
        int color = context.style.color.text;
        textColored(color, text);
    }

    public static void textColored(float r, float g, float b, float a, @NonNull String text) {
        int color = Color.rgba(r, g, b, a);
        textColored(color, text);
    }

    public static void textColored(int r, int g, int b, int a, @NonNull String text) {
        int color = Color.rgba(r, g, b, a);
        textColored(color, text);
    }

    public static void textColored(int color, @NonNull String text) {
        Vector2f cursor = context.windowCurrent.cursorPosition;

        Window window = context.windowCurrent;
        window.updateCursorBeforeDrawing();

        int cursorDelta =
                context.windowCurrent.drawList.addText(
                        context.fontSize, cursor.x, cursor.y, color, text);
        window.updateCursorAfterDrawing(
                cursorDelta, IkGuiImplLayout.getTextLineHeightWithSpacing());
    }

    public static void textDisabled(@NonNull String text) {
        int color = context.style.color.textDisabled;
        textColored(color, text);
    }

    public static void textUnformatted(@NonNull String text) {
        // TODO(ches) complete this
    }

    public static void textWrapped(@NonNull String text) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, boolean value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, float value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, float value, String format) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, int value) {
        // TODO(ches) complete this
    }

    public static void value(String prefix, long value) {
        // TODO(ches) complete this
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplText() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
