package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.Window;

import org.joml.Vector2f;

class IkGuiImplLayout {
    static Context context;

    public static void beginGroup() {
        // TODO(ches) complete this
    }

    public static void endGroup() {
        // TODO(ches) complete this
    }

    public static float getFrameHeight() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getFrameHeightWithSpacing() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getItemRectHeight() {
        // TODO(ches) complete this
        return 0;
    }

    public static void getItemRectMax(Vector2f value) {
        // TODO(ches) complete this
    }

    public static float getItemRectMaxX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getItemRectMaxY() {
        // TODO(ches) complete this
        return 0;
    }

    public static void getItemRectMin(Vector2f value) {
        // TODO(ches) complete this
    }

    public static float getItemRectMinX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getItemRectMinY() {
        // TODO(ches) complete this
        return 0;
    }

    public static void getItemRectSize(Vector2f value) {
        // TODO(ches) complete this
    }

    public static float getItemRectWidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static int getTextLineHeight() {
        float fontScale = (float) context.dpiScaleScreen / context.dpiScaleFont;
        return (int) (fontScale * context.fontSize);
    }

    public static int getTextLineHeightWithSpacing() {
        float fontScale = (float) context.dpiScaleScreen / context.dpiScaleFont;
        return (int) (fontScale * 1.1f * context.fontSize);
    }

    public static float getTreeNodeToLabelSpacing() {
        // TODO(ches) complete this
        return 0;
    }

    public static void indent(float width) {
        // TODO(ches) complete this
    }

    public static void newLine() {
        // TODO(ches) complete this
    }

    public static void popClipRect() {
        // TODO(ches) complete this
    }

    public static void popItemWidth() {
        // TODO(ches) complete this
    }

    public static void popTextWrapPos() {
        // TODO(ches) complete this
    }

    public static void pushClipRect(
            float minX, float minY, float maxX, float maxY, boolean intersectWithCurrentClipRect) {
        // TODO(ches) complete this
    }

    public static void pushItemWidth(float width) {
        // TODO(ches) complete this
    }

    public static void pushTextWrapPos(float wrapLocalPosX) {
        // TODO(ches) complete this
    }

    public static void sameLine(final float offsetFromStartX, float spacingAfterCurrent) {
        Window window = context.windowCurrent;
        Vector2f cursor = window.cursorPosition;

        if (spacingAfterCurrent == -1) {
            spacingAfterCurrent = context.style.variable.itemSpacing.x;
        }

        float newX;
        if (offsetFromStartX == 0) {
            newX = cursor.x + spacingAfterCurrent;
        } else {
            newX = window.cursorStartPosition.x + offsetFromStartX;
        }

        window.sameLine = true;
        cursor.set(newX, cursor.y);
    }

    public static void separator() {
        // TODO(ches) complete this
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplLayout() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
