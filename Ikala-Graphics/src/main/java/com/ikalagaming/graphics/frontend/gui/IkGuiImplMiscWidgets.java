package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;

class IkGuiImplMiscWidgets {

    static Context context;

    public static boolean beginCombo(String label, String previewValue, int comboFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean beginListBox(String label, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean button(String text, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean checkbox(String label, boolean initialState) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean checkbox(String label, IkBoolean active) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean checkboxFlags(String label, IkInt flags, int flagsValue) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorButton(
            String label, float[] color, int colorEditFlags, float width, float height) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorEdit3(String label, float[] value, int colorEditFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorEdit4(String label, float[] value, int colorEditFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorPicker3(String label, float[] value, int colorEditFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean colorPicker4(
            String label, float[] value, int colorEditFlags, float[] referenceColor) {
        // TODO(ches) complete this
        return false;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplMiscWidgets() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
