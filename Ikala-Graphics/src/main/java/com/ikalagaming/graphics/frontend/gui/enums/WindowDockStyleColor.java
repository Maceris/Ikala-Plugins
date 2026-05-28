package com.ikalagaming.graphics.frontend.gui.enums;

import lombok.NonNull;

/**
 * Types of colors that are stored at the time of begin() into Docked Windows. Currently stored in
 * an array.
 */
public enum WindowDockStyleColor {
    TEXT,
    TAB_HOVERED,
    TAB_SELECTED,
    TAB_SELECTED_OVERLINE,
    TAB_DIMMED,
    TAB_DIMMED_SELECTED,
    TAB_DIMMED_SELECTED_OVERLINE;

    /** The number of elements. */
    public static final int COUNT = WindowDockStyleColor.values().length;

    public static ColorType mapToColor(@NonNull WindowDockStyleColor color) {
        return switch (color) {
            case TEXT -> ColorType.TEXT;
            case TAB_HOVERED -> ColorType.TAB_HOVERED;
            case TAB_SELECTED -> ColorType.TAB_SELECTED;
            case TAB_SELECTED_OVERLINE -> ColorType.TAB_SELECTED_OVERLINE;
            case TAB_DIMMED -> ColorType.TAB_DIMMED;
            case TAB_DIMMED_SELECTED -> ColorType.TAB_DIMMED_SELECTED;
            case TAB_DIMMED_SELECTED_OVERLINE -> ColorType.TAB_DIMMED_SELECTED_OVERLINE;
        };
    }
}
