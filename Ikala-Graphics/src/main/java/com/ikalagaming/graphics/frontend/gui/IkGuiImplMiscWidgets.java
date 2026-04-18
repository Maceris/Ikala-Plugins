package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.SliderDataType;

import lombok.NonNull;

class IkGuiImplMiscWidgets {

    public static final String FLOAT_DEFAULT_FORMAT = "%.3f";
    public static final String INT_DEFAULT_FORMAT = "%d";
    public static final String DOUBLE_DEFAULT_FORMAT = "%.6f";

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

    public static boolean combo(
            String label, IkInt currentItem, String[] items, int popumaxHeightInItems) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat(
            String label,
            float[] value,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat2(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat3(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloat4(
            String label,
            float[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragFloatRange2(
            String label,
            float[] currentMin,
            float[] currentMax,
            float speed,
            float min,
            float max,
            String format,
            String formatMax,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt(
            String label,
            int[] value,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt2(
            String label,
            int[] values,
            float speed,
            float min,
            float max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt3(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragInt4(
            String label,
            int[] values,
            float speed,
            int min,
            int max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragIntRange2(
            String label,
            int[] currentMin,
            int[] currentMax,
            float speed,
            int min,
            int max,
            String format,
            String formatMax,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragScalar(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            float speed,
            double min,
            double max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean dragScalarN(
            String label,
            @NonNull SliderDataType dataType,
            @NonNull Object data,
            int components,
            float speed,
            double min,
            double max,
            String format,
            int sliderFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static void dummy(float width, float height) {
        // TODO(ches) complete this
    }

    public static void endCombo() {
        // TODO(ches) complete this
    }

    public static void endListBox() {
        // TODO(ches) complete this
    }

    public static void image(
            int textureID, float width, float height, float u0, float v0, float u1, float v1) {
        // TODO(ches) complete this
    }

    public static boolean imageButton(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int background,
            int tint) {
        // TODO(ches) complete this
        return false;
    }

    public static void imageWithBackground(
            int textureID,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            int background,
            int tint) {
        // TODO(ches) complete this
    }

    public static boolean inputDouble(
            String label,
            double[] value,
            double step,
            double stepFast,
            String format,
            int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat(
            String label,
            float[] value,
            float step,
            float stepFast,
            String format,
            int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat2(
            String label, float[] values, String format, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat3(
            String label, float[] values, String format, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputFloat4(
            String label, float[] values, String format, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputInt(
            String label, int[] value, int step, int stepFast, int inputTextFlags) {
        return false;
    }

    public static boolean inputInt2(String label, int[] values, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputInt3(String label, int[] values, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    public static boolean inputInt4(String label, int[] values, int inputTextFlags) {
        // TODO(ches) complete this
        return false;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplMiscWidgets() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
