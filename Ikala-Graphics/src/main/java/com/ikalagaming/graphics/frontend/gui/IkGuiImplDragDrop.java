package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;

class IkGuiImplDragDrop {

    static Context context;

    public static <T> T acceptDragDropPayload(Class<T> aClass, int dragDropFlags) {
        // TODO(ches) complete this
        return null;
    }

    public static <T> T acceptDragDropPayload(String dataType, int dragDropFlags) {
        // TODO(ches) complete this
        return null;
    }

    public static boolean beginDragDropSource() {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginDragDropSource(int dragDropFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginDragDropTarget() {
        // TODO(ches) complete this
        return false;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplDragDrop() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
