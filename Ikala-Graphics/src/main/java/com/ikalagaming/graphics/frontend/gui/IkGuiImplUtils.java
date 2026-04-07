package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.util.Color;

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

    /** Private constructor so this is not instantiated. */
    private IkGuiImplUtils() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
