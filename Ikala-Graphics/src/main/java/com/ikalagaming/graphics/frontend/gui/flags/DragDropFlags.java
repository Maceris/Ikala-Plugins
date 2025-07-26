package com.ikalagaming.graphics.frontend.gui.flags;

public class DragDropFlags {
    public static final int NONE = 0;
    public static final int SOURCE_NO_PREVIEW_TOOLTIP = 1;
    public static final int SOURCE_NO_DISABLE_HOVER = 1 << 1;
    public static final int SOURCE_NO_HOLD_TO_OPEN_OTHERS = 1 << 2;
    public static final int SOURCE_ALLOW_NULL_ID = 1 << 3;
    public static final int SOURCE_EXTERN = 1 << 4;
    public static final int SOURCE_AUTO_EXPIRE_PAYLOAD = 1 << 5;
    public static final int ACCEPT_BEFORE_DELIVERY = 1 << 6;
    public static final int ACCEPT_NO_DRAW_DEFAULT_RECT = 1 << 7;
    public static final int ACCEPT_NO_PREVIEW_TOOLTIP = 1 << 8;

    public static final int ACCEPT_PEEK_ONLY = ACCEPT_BEFORE_DELIVERY | ACCEPT_NO_DRAW_DEFAULT_RECT;
}
