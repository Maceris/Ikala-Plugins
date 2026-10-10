package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.enums.ColorButtonPosition;
import com.ikalagaming.graphics.gui.enums.StyleVariable;
import com.ikalagaming.graphics.gui.enums.WindowMenuButtonPosition;
import com.ikalagaming.graphics.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

@Slf4j
public class StyleVariables {
    /** Values between 0 and 1. */
    public float alpha;

    /** Values between 0 and 1. */
    public final Vector2f buttonTextAlign;

    /** Radius of rounding for menu items and menus. Values between 0 and 12. */
    public float menuItemRounding;

    /** Radius of rounding for selectables. Values between 0 and 12. */
    public float selectableRounding;

    /** Thickness of the line drawn by separator(), must be at least 1. Values between 1 and 10. */
    public float separatorSize;

    /** Scale of the mouse cursor, used to offset tooltips away from the cursor. */
    public float mouseCursorScale;

    /** Size of R/G/B/A color markers on drags and sliders using SliderFlags.COLOR_MARKERS. */
    public float colorMarkerSize;

    /** Values between 0 and 20. */
    public final Vector2f cellPadding;

    /** Values between 0 and 1. */
    public float childBorderSize;

    /** Values between 0 and 12. */
    public float childRounding;

    public @NonNull ColorButtonPosition colorButtonPosition;

    /** Additional multiplier for alpha of disabled items. Values between 0 and 1. */
    public float disabledAlpha;

    /** Dock nodes have their own close button to close all docked windows. */
    public boolean dockingNodeHasCloseButton;

    /** Thickness of the resizing border between docked windows. Values between 0 and 12. */
    public float dockingSeparatorSize;

    /**
     * Radius of the drag and drop target frame. Values between 0 and 12, when negative
     * frameRounding is used.
     */
    public float dragDropTargetRounding;

    /** Thickness of the drag and drop target border. */
    public float dragDropTargetBorderSize;

    /** Size to expand the drag and drop target from the actual target item size. */
    public float dragDropTargetPadding;

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

    /** Thickness of the overline drawn on the selected tab. Values between 0 and 3. */
    public float tabBarOverlineSize;

    /**
     * The default way to draw lines connecting the tree node hierarchy, one of
     * TreeNodeFlags.DRAW_LINES_NONE, DRAW_LINES_FULL or DRAW_LINES_TO_NODES.
     *
     * @see com.ikalagaming.graphics.gui.flags.TreeNodeFlags
     */
    public int treeLinesFlags;

    /** Thickness of the lines connecting the tree node hierarchy. */
    public float treeLinesSize;

    /** Radius of the corners where lines to child nodes meet the vertical line. */
    public float treeLinesRounding;

    /** Minimum tab width, to make tabs larger than their contents. */
    public float tabMinWidthBase;

    /** Minimum tab width after shrinking, when using the mixed fitting policy. */
    public float tabMinWidthShrink;

    /**
     * When the close button is visible on selected tabs. -1 means always visible, 0 means visible
     * when hovered, greater than 0 means visible when hovered if at least that wide.
     */
    public float tabCloseButtonMinWidthSelected;

    /**
     * When the close button is visible on unselected tabs. -1 means always visible, 0 means visible
     * when hovered, greater than 0 means visible when hovered if at least that wide.
     * Float.MAX_VALUE means never.
     */
    public float tabCloseButtonMinWidthUnselected;

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

    /**
     * How far in from each edge, in pixels, the background and title bar of top level windows fade
     * from transparent to their regular color, as (left, top, right, bottom). 0 means a hard edge.
     * Widgets inside the window, child windows and popups don't fade. Values between 0 and 64.
     */
    public final Vector4f windowEdgeFade;

    /**
     * Invert the window edge fade, so the fading edges are opaque and fade to transparent further
     * in. This has no effect on edges that don't fade.
     */
    public boolean windowEdgeFadeInvert;

    public @NonNull WindowMenuButtonPosition windowMenuButtonPosition;
    public final Vector2f windowMinSize;

    /** Values between 0 and 20. */
    public final Vector2f windowPadding;

    /** Values between 0 and 12. */
    public float windowRounding;

    /** Values between 0 and 1. */
    public final Vector2f windowTitleAlign;

    /**
     * Apply to regular windows: amount which we enforce to keep visible when moving near edges of
     * your screen.
     */
    public final Vector2f displayWindowPadding;

    /** Thickness of the border around image() calls. Values between 0 and 2. */
    public float imageBorderSize;

    /** Rounding of image() calls. Values between 0 and 12. */
    public float imageRounding;

    /** Thickness of the cursor/caret in inputText(). */
    public float inputTextCursorSize;

    /**
     * Apply to every window, menu, popup, tooltip: amount where we avoid displaying contents.
     * Adjust if you cannot see the edges of your screen (e.g. on a TV where scaling has not been
     * configured).
     */
    public final Vector2f displaySafeAreaPadding;

    /** Padding of scrollbar grab within its frame (same for both axes). */
    public float scrollbarPadding;

    /**
     * Delay for isItemHovered(HoveredFlags.STATIONARY). Time required to consider mouse stationary,
     * in milliseconds.
     */
    public long hoverStationaryDelay;

    /** Delay for isItemHovered(HoveredFlags.DELAY_SHORT), in milliseconds. */
    public long hoverDelayShort;

    /** Delay for isItemHovered(HoveredFlags.DELAY_NORMAL), in milliseconds. */
    public long hoverDelayNormal;

    /**
     * Default flags when using isItemHovered(HoveredFlags.FOR_TOOLTIP) or beginItemTooltip() /
     * setItemTooltip() while using the mouse.
     *
     * @see com.ikalagaming.graphics.gui.flags.HoveredFlags
     */
    public int hoverFlagsForTooltipMouse;

    /**
     * Default flags when using isItemHovered(HoveredFlags.FOR_TOOLTIP) or beginItemTooltip() /
     * setItemTooltip() while using keyboard/gamepad.
     *
     * @see com.ikalagaming.graphics.gui.flags.HoveredFlags
     */
    public int hoverFlagsForTooltipNav;

    public StyleVariables() {
        alpha = 1.0f;
        buttonTextAlign = new Vector2f(0.5f, 0.5f);
        cellPadding = new Vector2f(4, 2);
        menuItemRounding = 0;
        selectableRounding = 0;
        separatorSize = 1;
        mouseCursorScale = 1;
        colorMarkerSize = 3;
        childBorderSize = 1;
        childRounding = 0;
        colorButtonPosition = ColorButtonPosition.RIGHT;
        disabledAlpha = 0.6f;
        dockingNodeHasCloseButton = true;
        dockingSeparatorSize = 2;
        dragDropTargetRounding = 0;
        dragDropTargetBorderSize = 2;
        dragDropTargetPadding = 3;
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
        tabBarOverlineSize = 1;
        treeLinesFlags = TreeNodeFlags.DRAW_LINES_NONE;
        treeLinesSize = 1;
        treeLinesRounding = 0;
        tabMinWidthBase = 1;
        tabMinWidthShrink = 80;
        tabCloseButtonMinWidthSelected = -1;
        tabCloseButtonMinWidthUnselected = 0;
        tabBorderSize = 0;
        tableAngledHeadersAngle = 35;
        tableAngledHeadersTextAlign = new Vector2f(0.5f, 0.0f);
        tabRounding = 4;
        touchExtraPadding = new Vector2f(0, 0);
        windowBorderHoverPadding = 4;
        windowBorderSize = 1;
        windowEdgeFade = new Vector4f(0, 0, 0, 0);
        windowEdgeFadeInvert = false;
        windowMenuButtonPosition = WindowMenuButtonPosition.LEFT;
        windowMinSize = new Vector2f(32, 32);
        windowPadding = new Vector2f(8, 8);
        windowRounding = 0;
        windowTitleAlign = new Vector2f(0.0f, 0.5f);
        displayWindowPadding = new Vector2f(19, 19);
        imageBorderSize = 0;
        imageRounding = 0;
        inputTextCursorSize = 1;
        displaySafeAreaPadding = new Vector2f(3, 3);
        scrollbarPadding = 2;
        hoverStationaryDelay = 150;
        hoverDelayShort = 150;
        hoverDelayNormal = 400;
        hoverFlagsForTooltipMouse =
                HoveredFlags.STATIONARY
                        | HoveredFlags.DELAY_SHORT
                        | HoveredFlags.ALLOW_WHEN_DISABLED;
        hoverFlagsForTooltipNav =
                HoveredFlags.NO_SHARED_DELAY
                        | HoveredFlags.DELAY_NORMAL
                        | HoveredFlags.ALLOW_WHEN_DISABLED;
    }

    /**
     * Create a copy of other style variables.
     *
     * @param other The variables to copy.
     */
    public StyleVariables(@NonNull StyleVariables other) {
        this();
        set(other);
    }

    /**
     * Copy all the values from other style variables.
     *
     * @param other The variables to copy.
     */
    public void set(@NonNull StyleVariables other) {
        alpha = other.alpha;
        buttonTextAlign.set(other.buttonTextAlign);
        menuItemRounding = other.menuItemRounding;
        selectableRounding = other.selectableRounding;
        separatorSize = other.separatorSize;
        mouseCursorScale = other.mouseCursorScale;
        colorMarkerSize = other.colorMarkerSize;
        cellPadding.set(other.cellPadding);
        childBorderSize = other.childBorderSize;
        childRounding = other.childRounding;
        colorButtonPosition = other.colorButtonPosition;
        disabledAlpha = other.disabledAlpha;
        dockingNodeHasCloseButton = other.dockingNodeHasCloseButton;
        dockingSeparatorSize = other.dockingSeparatorSize;
        dragDropTargetRounding = other.dragDropTargetRounding;
        dragDropTargetBorderSize = other.dragDropTargetBorderSize;
        dragDropTargetPadding = other.dragDropTargetPadding;
        frameBorderSize = other.frameBorderSize;
        framePadding.set(other.framePadding);
        frameRounding = other.frameRounding;
        grabMinSize = other.grabMinSize;
        grabRounding = other.grabRounding;
        indentSpacing = other.indentSpacing;
        itemInnerSpacing.set(other.itemInnerSpacing);
        itemSpacing.set(other.itemSpacing);
        logSliderDeadzone = other.logSliderDeadzone;
        popupBorderSize = other.popupBorderSize;
        popupRounding = other.popupRounding;
        scrollbarRounding = other.scrollbarRounding;
        scrollbarSize = other.scrollbarSize;
        selectableTextAlign.set(other.selectableTextAlign);
        separatorTextAlign.set(other.separatorTextAlign);
        separatorTextBorderSize = other.separatorTextBorderSize;
        separatorTextPadding.set(other.separatorTextPadding);
        tabBarBorderSize = other.tabBarBorderSize;
        tabBarOverlineSize = other.tabBarOverlineSize;
        treeLinesFlags = other.treeLinesFlags;
        treeLinesSize = other.treeLinesSize;
        treeLinesRounding = other.treeLinesRounding;
        tabMinWidthBase = other.tabMinWidthBase;
        tabMinWidthShrink = other.tabMinWidthShrink;
        tabCloseButtonMinWidthSelected = other.tabCloseButtonMinWidthSelected;
        tabCloseButtonMinWidthUnselected = other.tabCloseButtonMinWidthUnselected;
        tabBorderSize = other.tabBorderSize;
        tableAngledHeadersAngle = other.tableAngledHeadersAngle;
        tableAngledHeadersTextAlign.set(other.tableAngledHeadersTextAlign);
        tabRounding = other.tabRounding;
        touchExtraPadding.set(other.touchExtraPadding);
        windowBorderHoverPadding = other.windowBorderHoverPadding;
        windowBorderSize = other.windowBorderSize;
        windowEdgeFade.set(other.windowEdgeFade);
        windowEdgeFadeInvert = other.windowEdgeFadeInvert;
        windowMenuButtonPosition = other.windowMenuButtonPosition;
        windowMinSize.set(other.windowMinSize);
        windowPadding.set(other.windowPadding);
        windowRounding = other.windowRounding;
        windowTitleAlign.set(other.windowTitleAlign);
        displayWindowPadding.set(other.displayWindowPadding);
        imageBorderSize = other.imageBorderSize;
        imageRounding = other.imageRounding;
        inputTextCursorSize = other.inputTextCursorSize;
        displaySafeAreaPadding.set(other.displaySafeAreaPadding);
        scrollbarPadding = other.scrollbarPadding;
        hoverStationaryDelay = other.hoverStationaryDelay;
        hoverDelayShort = other.hoverDelayShort;
        hoverDelayNormal = other.hoverDelayNormal;
        hoverFlagsForTooltipMouse = other.hoverFlagsForTooltipMouse;
        hoverFlagsForTooltipNav = other.hoverFlagsForTooltipNav;
    }

    public void setStyleVarFloat(@NonNull StyleVariable variable, float value) {
        if (variable.getDimensions() != 1) {
            IkGuiInternal.reportError(
                    log,
                    "Style variable {} has {} dimensions, 1 float provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            IkGuiInternal.reportError(
                    log,
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
            case DOCKING_SEPARATOR_SIZE:
                dockingSeparatorSize = value;
                break;
            case DRAG_DROP_TARGET_ROUNDING:
                dragDropTargetRounding = value;
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
            case IMAGE_BORDER_SIZE:
                imageBorderSize = value;
                break;
            case IMAGE_ROUNDING:
                imageRounding = value;
                break;
            case INDENT_SPACING:
                indentSpacing = value;
                break;
            case LOG_SLIDER_DEADZONE:
                logSliderDeadzone = value;
                break;
            case MENU_ITEM_ROUNDING:
                menuItemRounding = value;
                break;
            case SELECTABLE_ROUNDING:
                selectableRounding = value;
                break;
            case SEPARATOR_SIZE:
                separatorSize = value;
                break;
            case POPUP_BORDER_SIZE:
                popupBorderSize = value;
                break;
            case POPUP_ROUNDING:
                popupRounding = value;
                break;
            case SCROLLBAR_PADDING:
                scrollbarPadding = value;
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
            case TAB_BAR_OVERLINE_SIZE:
                tabBarOverlineSize = value;
                break;
            case TREE_LINES_ROUNDING:
                treeLinesRounding = value;
                break;
            case TREE_LINES_SIZE:
                treeLinesSize = value;
                break;
            case TAB_BORDER_SIZE:
                tabBorderSize = value;
                break;
            case TAB_MIN_WIDTH_BASE:
                tabMinWidthBase = value;
                break;
            case TAB_MIN_WIDTH_SHRINK:
                tabMinWidthShrink = value;
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
                IkGuiInternal.reportError(
                        log,
                        "Trying to set 1 float value for unexpected style variable {}",
                        variable);
                break;
        }
    }

    public void setStyleVarInt(@NonNull StyleVariable variable, int value) {
        if (variable.getDimensions() != 1) {
            IkGuiInternal.reportError(
                    log,
                    "Style variable {} has {} dimensions, 1 int provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Integer.class) {
            IkGuiInternal.reportError(
                    log,
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
                    IkGuiInternal.reportError(log, "Invalid color button position value {}", value);
                }
                break;
            case WINDOW_EDGE_FADE_INVERT:
                windowEdgeFadeInvert = value != 0;
                break;
            case WINDOW_MENU_BUTTON_POSITION:
                try {
                    windowMenuButtonPosition = WindowMenuButtonPosition.fromInteger(value);
                } catch (IllegalArgumentException ignored) {
                    IkGuiInternal.reportError(
                            log, "Invalid window menu button position value {}", value);
                }
                break;
            default:
                IkGuiInternal.reportError(
                        log,
                        "Trying to set 1 int value for unexpected style variable {}",
                        variable);
                break;
        }
    }

    public void setStyleVarFloat2(@NonNull StyleVariable variable, float x, float y) {
        if (variable.getDimensions() != 2) {
            IkGuiInternal.reportError(
                    log,
                    "Style variable {} has {} dimensions, 2 floats provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            IkGuiInternal.reportError(
                    log,
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
                IkGuiInternal.reportError(
                        log,
                        "Trying to set 2 float values for unexpected style variable {}",
                        variable);
                break;
        }
    }

    public void setStyleVarFloat4(
            @NonNull StyleVariable variable, float x, float y, float z, float w) {
        if (variable.getDimensions() != 4) {
            IkGuiInternal.reportError(
                    log,
                    "Style variable {} has {} dimensions, 4 floats provided",
                    variable,
                    variable.getDimensions());
            return;
        }
        if (variable.getExpectedType() != Float.class) {
            IkGuiInternal.reportError(
                    log,
                    "Style variable {} expects {} values, Floats provided",
                    variable,
                    variable.getExpectedType().getSimpleName());
            return;
        }
        for (float value : new float[] {x, y, z, w}) {
            if (value < variable.getMinValue() || value > variable.getMaxValue()) {
                log.warn(
                        "Variable {} value outside the expected float range({}, {})",
                        variable,
                        variable.getMinValue(),
                        variable.getMaxValue());
            }
        }

        switch (variable) {
            case WINDOW_EDGE_FADE:
                windowEdgeFade.set(x, y, z, w);
                break;
            default:
                IkGuiInternal.reportError(
                        log,
                        "Trying to set 4 float values for unexpected style variable {}",
                        variable);
                break;
        }
    }
}
