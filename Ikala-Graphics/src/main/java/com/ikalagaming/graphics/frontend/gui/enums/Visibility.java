package com.ikalagaming.graphics.frontend.gui.enums;

/** When we should display something like scrollbars. */
public enum Visibility {
    /** It should always be displayed, even if pointless. */
    ALWAYS,
    /** It should only show if it would be needed. */
    IF_REQUIRED,
    /** It should never show, even if it would be needed. */
    NEVER,
}
