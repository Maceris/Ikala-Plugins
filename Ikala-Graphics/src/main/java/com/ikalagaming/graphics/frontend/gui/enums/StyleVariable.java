package com.ikalagaming.graphics.frontend.gui.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StyleVariable {
    ALPHA(1, Float.class, 0, 1),
    BUTTON_TEXT_ALIGN(2, Float.class, 0, 1),
    CELL_PADDING(2, Float.class, 0, 20),
    CHILD_BORDER_SIZE(1, Float.class, 0, 1),
    CHILD_ROUNDING(1, Float.class, 0, 12),
    COLOR_BUTTON_POSITION(1, Integer.class, 0, 1),
    DISABLED_ALPHA(1, Float.class, 0, 1),
    FRAME_BORDER_SIZE(1, Float.class, 0, 1),
    FRAME_PADDING(2, Float.class, 0, 20),
    FRAME_ROUNDING(1, Float.class, 0, 12),
    GRAB_MIN_SIZE(1, Float.class, 1, 20),
    GRAB_ROUNDING(1, Float.class, 0, 12),
    INDENT_SPACING(1, Float.class, 0, 30),
    ITEM_INNER_SPACING(2, Float.class, 0, 20),
    ITEM_SPACING(2, Float.class, 0, 20),
    LOG_SLIDER_DEADZONE(1, Float.class, 0, 12),
    POPUP_BORDER_SIZE(1, Float.class, 0, 1),
    POPUP_ROUNDING(1, Float.class, 0, 12),
    SCROLLBAR_ROUNDING(1, Float.class, 0, 12),
    SCROLLBAR_SIZE(1, Float.class, 1, 20),
    SELECTABLE_TEXT_ALIGN(2, Float.class, 0, 1),
    SEPARATOR_TEXT_ALIGN(2, Float.class, 0, 1),
    SEPARATOR_TEXT_BORDER_SIZE(1, Float.class, 0, 10),
    SEPARATOR_TEXT_PADDING(2, Float.class, 0, 40),
    TAB_BAR_BORDER_SIZE(1, Float.class, 0, 2),
    TABLE_ANGLED_HEADERS_ANGLE(1, Float.class, -50, 50),
    TABLE_ANGLED_HEADERS_TEXT_ALIGN(2, Float.class, 0, 1),
    TAB_ROUNDING(1, Float.class, 0, 12),
    TOUCH_EXTRA_PADDING(2, Float.class, 0, 10),
    WINDOW_BORDER_HOVER_PADDING(1, Float.class, 0, 5),
    WINDOW_BORDER_SIZE(1, Float.class, 0, 1),
    WINDOW_MENU_BUTTON_POSITION(1, Integer.class, 0, 2),
    WINDOW_MIN_SIZE(2, Float.class, 0, Integer.MAX_VALUE),
    WINDOW_PADDING(2, Float.class, 0, 20),
    WINDOW_ROUNDING(1, Float.class, 0, 12),
    WINDOW_TITLE_ALIGN(2, Float.class, 0, 1),
    ;

    private final int dimensions;
    private final Class<?> expectedType;
    private final int minValue;
    private final int maxValue;
}
