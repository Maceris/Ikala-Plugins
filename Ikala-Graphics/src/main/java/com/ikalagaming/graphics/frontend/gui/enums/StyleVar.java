package com.ikalagaming.graphics.frontend.gui.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StyleVar {
    ALPHA(1),
    BUTTON_TEXT_ALIGN(2),
    CELL_PADDING(2),
    CHILD_BORDER_SIZE(1),
    CHILD_ROUNDING(1),
    DISABLED_ALPHA(1),
    FRAME_BORDER_SIZE(1),
    FRAME_PADDING(2),
    FRAME_ROUNDING(1),
    GRAB_MIN_SIZE(1),
    GRAB_ROUNDING(1),
    INDENT_SPACING(1),
    ITEM_INNER_SPACING(2),
    ITEM_SPACING(2),
    POPUP_BORDER_SIZE(1),
    POPUP_ROUNDING(1),
    SCROLLBAR_ROUNDING(1),
    SCROLLBAR_SIZE(1),
    SELECTABLE_TEXT_ALIGN(2),
    TAB_ROUNDING(1),
    WINDOW_BORDER_SIZE(1),
    WINDOW_MIN_SIZE(2),
    WINDOW_PADDING(2),
    WINDOW_ROUNDING(1),
    WINDOW_TITLE_ALIGN(2),
    ;

    private final int dimensions;
}
