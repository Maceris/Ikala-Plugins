package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

@Slf4j
class IkGuiImplUtils {

    static Context context;

    public static int applyGlobalAlpha(int color, boolean disabled) {
        int result = color;
        result = Color.multiplyAlpha(result, context.style.variable.alpha);
        if (disabled) {
            result = Color.multiplyAlpha(result, context.style.variable.disabledAlpha);
        }
        return result;
    }

    public static void beginDisabled(boolean disabled) {
        // TODO(ches) complete this
    }

    public static void calcTextSize(
            Vector2f result, String text, boolean hideTextAfterDoubleHash, float wrapWidth) {
        // TODO(ches) complete this
    }

    public static float calculateItemWidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static void colorConvertHSVtoRGB(float[] in, float[] out) {
        // TODO(ches) complete this
        if (in.length != 3 || out.length != 3) {
            log.error("colorConvertHSVtoRGB expects arrays of size 3");
            return;
        }
    }

    public static void colorConvertRGBtoHSV(float[] in, float[] out) {
        // TODO(ches) complete this
        if (in.length != 3 || out.length != 3) {
            log.error("colorConvertHSVtoRGB expects arrays of size 3");
            return;
        }
    }

    public static void colorConvertU32ToFloat4(int in, Vector4f out) {
        // TODO(ches) complete this
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplUtils() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
