package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.ColorButtonPosition;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.enums.WindowMenuButtonPosition;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

@Slf4j
public class StyleVariables {
    /** Values between 0 and 1. */
    public float alpha;

    /** Values between 0 and 1. */
    public final Vector2f buttonTextAlign;

    /** Values between 0 and 20. */
    public final Vector2f cellPadding;

    /** Values between 0 and 1. */
    public float childBorderSize;

    /** Values between 0 and 12. */
    public float childRounding;

    public @NonNull ColorButtonPosition colorButtonPosition;

    /** Additional multiplier for alpha of disabled items. Values between 0 and 1. */
    public float disabledAlpha;

    /** Values between 0 and 1. */
    public float frameBorderSize;

    /** Values between 0 and 20. */
    public final Vector2f framePadding;

    /** Values between 0 and 12. */
    public float frameRounding;

    /** Values between 1 and 20. */
    public float grabMinSize;

    /** Values between 0 and 12. */
    public float grabRounding;

    /** Values between 0 and 30. */
    public float indentSpacing;

    /** Values between 0 and 20. */
    public final Vector2f itemInnerSpacing;

    /** Values between 0 and 20. */
    public final Vector2f itemSpacing;

    /** Dead zone around logarithmic sliders that cross zero. Values between 0 and 12. */
    public float logSliderDeadzone;

    /** Values between 0 and 1. */
    public float popupBorderSize;

    /** Values between 0 and 12. */
    public float popupRounding;

    /** Values between 0 and 12. */
    public float scrollbarRounding;

    /** Values between 1 and 20. */
    public float scrollbarSize;

    /** Values between 0 and 1. */
    public final Vector2f selectableTextAlign;

    /** Values between 0 and 1. */
    public final Vector2f separatorTextAlign;

    /** Values between 0 and 10. */
    public float separatorTextBorderSize;

    /** Values between 0 and 40. */
    public final Vector2f separatorTextPadding;

    /** Values between 0 and 2. */
    public float tabBarBorderSize;

    /** Values between 0 and 1. */
    public float tabBorderSize;

    /** Angle in degrees. Values between -50 and 50. */
    public float tableAngledHeadersAngle;

    /** Values between 0 and 1. */
    public Vector2f tableAngledHeadersTextAlign;

    /** Values between 0 and 12. */
    public float tabRounding;

    /** Values between 0 and 10. */
    public final Vector2f touchExtraPadding;

    /**
     * Extra space around the border of a window that counts as still hovering over the window, to
     * make resizing easier.
     */
    public float windowBorderHoverPadding;

    /** Values between 0 and 1. */
    public float windowBorderSize;

    public @NonNull WindowMenuButtonPosition windowMenuButtonPosition;
    public final Vector2f windowMinSize;

    /** Values between 0 and 20. */
    public final Vector2f windowPadding;

    /** Values between 0 and 12. */
    public float windowRounding;

    /** Values between 0 and 1. */
    public final Vector2f windowTitleAlign;

    public StyleVariables() {
        alpha = 1.0f;
        buttonTextAlign = new Vector2f(0.5f, 0.5f);
        cellPadding = new Vector2f(4, 2);
        childBorderSize = 1;
        childRounding = 0;
        colorButtonPosition = ColorButtonPosition.RIGHT;
        disabledAlpha = 0.6f;
        frameBorderSize = 0;
        framePadding = new Vector2f(4, 3);
        frameRounding = 0;
        grabMinSize = 12;
        grabRounding = 0;
        indentSpacing = 21;
        itemInnerSpacing = new Vector2f(4, 4);
        itemSpacing = new Vector2f(8, 4);
        logSliderDeadzone = 4;
        popupBorderSize = 1;
        popupRounding = 0;
        scrollbarRounding = 9;
        scrollbarSize = 14;
        selectableTextAlign = new Vector2f(0.0f, 0.0f);
        separatorTextBorderSize = 3;
        separatorTextAlign = new Vector2f(0.0f, 0.5f);
        separatorTextPadding = new Vector2f(20, 3);
        tabBarBorderSize = 1;
        tabBorderSize = 0;
        tableAngledHeadersAngle = 35;
        tableAngledHeadersTextAlign = new Vector2f(0.5f, 0.0f);
        tabRounding = 4;
        touchExtraPadding = new Vector2f(0, 0);
        windowBorderHoverPadding = 4;
        windowBorderSize = 1;
        windowMenuButtonPosition = WindowMenuButtonPosition.LEFT;
        windowMinSize = new Vector2f(32, 32);
        windowPadding = new Vector2f(8, 8);
        windowRounding = 0;
        windowTitleAlign = new Vector2f(0.0f, 0.5f);
    }

    public void setStyleVarFloat(@NonNull StyleVariable variable, float value) {
        if (variable.getDimensions() != 1) {
            log.error(
                    "Style variable {} has {} dimensions, 1 float provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            log.error(
                    "Style variable {} expects a {} value, Float provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (value < variable.getMinValue() || value > variable.getMaxValue()) {
            log.warn(
                    "Variable {} outside the expected float range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }

        switch (variable) {
            case ALPHA:
                alpha = value;
                break;
            case DISABLED_ALPHA:
                disabledAlpha = value;
                break;
            case CHILD_BORDER_SIZE:
                childBorderSize = value;
                break;
            case CHILD_ROUNDING:
                childRounding = value;
                break;
            case FRAME_BORDER_SIZE:
                frameBorderSize = value;
                break;
            case FRAME_ROUNDING:
                frameRounding = value;
                break;
            case GRAB_MIN_SIZE:
                grabMinSize = value;
                break;
            case GRAB_ROUNDING:
                grabRounding = value;
                break;
            case INDENT_SPACING:
                indentSpacing = value;
                break;
            case LOG_SLIDER_DEADZONE:
                logSliderDeadzone = value;
                break;
            case POPUP_BORDER_SIZE:
                popupBorderSize = value;
                break;
            case POPUP_ROUNDING:
                popupRounding = value;
                break;
            case SCROLLBAR_ROUNDING:
                scrollbarRounding = value;
                break;
            case SCROLLBAR_SIZE:
                scrollbarSize = value;
                break;
            case SEPARATOR_TEXT_BORDER_SIZE:
                separatorTextBorderSize = value;
                break;
            case TABLE_ANGLED_HEADERS_ANGLE:
                tableAngledHeadersAngle = value;
                break;
            case TAB_BAR_BORDER_SIZE:
                tabBarBorderSize = value;
                break;
            case TAB_ROUNDING:
                tabRounding = value;
                break;
            case WINDOW_BORDER_HOVER_PADDING:
                windowBorderHoverPadding = value;
                break;
            case WINDOW_BORDER_SIZE:
                windowBorderSize = value;
                break;
            case WINDOW_ROUNDING:
                windowRounding = value;
                break;
            default:
                log.error("Trying to set 1 float value for unexpected style variable {}", variable);
                break;
        }
    }

    public void setStyleVarInt(@NonNull StyleVariable variable, int value) {
        if (variable.getDimensions() != 1) {
            log.error(
                    "Style variable {} has {} dimensions, 1 int provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Integer.class) {
            log.error(
                    "Style variable {} expects a {} value, Integer provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (value < variable.getMinValue() || value > variable.getMaxValue()) {
            log.warn(
                    "Variable {} outside the expected int range ({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }

        switch (variable) {
            case COLOR_BUTTON_POSITION:
                try {
                    colorButtonPosition = ColorButtonPosition.fromInteger(value);
                } catch (IllegalArgumentException ignored) {
                    log.error("Invalid color button position value {}", value);
                }
                break;
            case WINDOW_MENU_BUTTON_POSITION:
                try {
                    windowMenuButtonPosition = WindowMenuButtonPosition.fromInteger(value);
                } catch (IllegalArgumentException ignored) {
                    log.error("Invalid window menu button position value {}", value);
                }
                break;
            default:
                log.error("Trying to set 1 int value for unexpected style variable {}", variable);
                break;
        }
    }

    public void setStyleVarFloat2(@NonNull StyleVariable variable, float x, float y) {
        if (variable.getDimensions() != 2) {
            log.error(
                    "Style variable {} has {} dimensions, 2 floats provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            log.error(
                    "Style variable {} expects {} values, Floats provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        if (x < variable.getMinValue() || x > variable.getMaxValue()) {
            log.warn(
                    "Variable {} x value outside the expected float range({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }
        if (y < variable.getMinValue() || y > variable.getMaxValue()) {
            log.warn(
                    "Variable {} y value outside the expected float range({}, {})",
                    variable,
                    variable.getMinValue(),
                    variable.getMaxValue());
        }

        switch (variable) {
            case BUTTON_TEXT_ALIGN:
                buttonTextAlign.set(x, y);
                break;
            case CELL_PADDING:
                cellPadding.set(x, y);
                break;
            case FRAME_PADDING:
                framePadding.set(x, y);
                break;
            case ITEM_INNER_SPACING:
                itemInnerSpacing.set(x, y);
                break;
            case ITEM_SPACING:
                itemSpacing.set(x, y);
                break;
            case SELECTABLE_TEXT_ALIGN:
                selectableTextAlign.set(x, y);
                break;
            case SEPARATOR_TEXT_ALIGN:
                separatorTextAlign.set(x, y);
                break;
            case SEPARATOR_TEXT_PADDING:
                separatorTextPadding.set(x, y);
                break;
            case TABLE_ANGLED_HEADERS_TEXT_ALIGN:
                tableAngledHeadersTextAlign.set(x, y);
                break;
            case TOUCH_EXTRA_PADDING:
                touchExtraPadding.set(x, y);
                break;
            case WINDOW_MIN_SIZE:
                windowMinSize.set(x, y);
                break;
            case WINDOW_PADDING:
                windowPadding.set(x, y);
                break;
            case WINDOW_TITLE_ALIGN:
                windowTitleAlign.set(x, y);
                break;
            default:
                log.error(
                        "Trying to set 2 float values for unexpected style variable {}", variable);
                break;
        }
    }
}
