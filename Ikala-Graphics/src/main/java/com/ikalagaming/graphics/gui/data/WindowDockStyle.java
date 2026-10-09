package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.WindowDockStyleColor;

/**
 * Backups of dock styles.
 *
 * @see WindowDockStyleColor
 */
public class WindowDockStyle {
    /** A list of the colors from {@link WindowDockStyleColor}, in order. */
    public final int[] colors;

    public WindowDockStyle() {
        colors = new int[WindowDockStyleColor.COUNT];
    }
}
