package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.ColorMod;
import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.DrawList;
import com.ikalagaming.graphics.frontend.gui.data.Viewport;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

@Slf4j
class IkGuiImplUtils {

    /**
     * Used to wrap a large frame count back to 0, which I like more than accidentally wrapping into
     * the negatives. We just need the numbers to be different each frame anyway, so this shouldn't
     * matter much.
     */
    private static final int FRAME_COUNT_CAP = 2_000_000_000;

    static Context context;

    public static int applyGlobalAlpha(int color, boolean disabled) {
        int result = color;
        result = Color.multiplyAlpha(result, context.style.variable.alpha);
        if (disabled) {
            result = Color.multiplyAlpha(result, context.style.variable.disabledAlpha);
        }
        return result;
    }

    public static void beginDisabled(boolean disabled) {
        // TODO(ches) complete this
    }

    public static void calcTextSize(
            Vector2f result, String text, boolean hideTextAfterDoubleHash, float wrapWidth) {
        // TODO(ches) complete this
    }

    public static float calculateItemWidth() {
        // TODO(ches) complete this
        return 0;
    }

    public static void colorConvertHSVtoRGB(float[] in, float[] out) {
        // TODO(ches) complete this
        if (in.length != 3 || out.length != 3) {
            log.error("colorConvertHSVtoRGB expects arrays of size 3");
            return;
        }
    }

    public static void colorConvertRGBtoHSV(float[] in, float[] out) {
        // TODO(ches) complete this
        if (in.length != 3 || out.length != 3) {
            log.error("colorConvertHSVtoRGB expects arrays of size 3");
            return;
        }
    }

    public static void colorConvertU32ToFloat4(int in, Vector4f out) {
        // TODO(ches) complete this
    }

    public static void endDisabled() {
        // TODO(ches) complete this
    }

    public static void endFrame() {
        // TODO(ches) complete this

        if (context.frameCountEnded == context.frameCount) {
            log.error("newFrame() must be called before endFrame()");
            return;
        }

        if (!context.styleVariableStack.isEmpty()) {
            log.error("Did not pop off all style variables before ending frame");
            context.styleVariableStack.clear();
        }

        // TODO(ches) update navigation
        // TODO(ches) update docking
        // TODO(ches) update drag and drop
        // TODO(ches) end the frame
        IkGuiInternal.updateMouseMovingWindowEndFrame();

        context.backgroundDrawList.prepareForRender();
        context.foregroundDrawList.prepareForRender();
        // TODO(ches) update viewports list
        // TODO(ches) sort the window list
        // TODO(ches) check the window parents/children are sane
        // TODO(ches) update textures
        // TODO(ches) unlock the font atlas
        context.io.mousePositionPrevious.set(context.io.mousePosition);
        context.io.appFocusLost = false;
        context.io.mouseWheel = 0.0f;
        context.io.mouseWheelH = 0.0f;
        // TODO(ches) clear input queue?

        // TODO(ches) call context hooks

        context.frameCountEnded = (context.frameCountEnded + 1) % FRAME_COUNT_CAP;
    }

    public static void newFrame() {
        // TODO(ches) complete this

        // TODO(ches) sanity checks for IO and configuration
        // TODO(ches) update settings
        // Updating frame time and count
        final long lastFrameStart = context.frameStartTime;
        context.frameStartTime = System.currentTimeMillis();
        if (lastFrameStart > 0) {
            context.io.deltaTime = context.frameStartTime - lastFrameStart;
        }
        context.time += context.io.deltaTime;
        context.frameCount = (context.frameCount + 1) % FRAME_COUNT_CAP;
        // TODO(ches) update window counts

        // Updating input events
        context.io.clearFrameSpecificValues();
        context.io.processInputEvents();

        // TODO(ches) update viewports

        // Update draw lists
        context.drawData.clear();
        context.backgroundDrawList.clear();
        context.foregroundDrawList.clear();
        context.drawData.drawLists.add(context.backgroundDrawList);
        context.drawData.drawLists.add(context.foregroundDrawList);
        // TODO(ches) update active IDs
        // TODO(ches) update hover delay
        // TODO(ches) update keyboard inputs
        // TODO(ches) update drag and drop

        IkGuiInternal.updateHoveredWindowAndCaptureFlags(context.io.mousePosition);
        IkGuiInternal.updateMouseMovingWindowNewFrame();

        // TODO(ches) update navigation
        context.io.updateMouseInputs();
        // TODO(ches) clean up transient buffers
        context.windowDisplayOrder.clear();
        context.windowFocusOrder.clear();
        // TODO(ches) create fallback window
    }

    public static void render() {
        // TODO(ches) complete this

        if (context.frameCountEnded != context.frameCount) {
            IkGui.endFrame();
        }

        context.frameCountRendered = (context.frameCountRendered + 1) % FRAME_COUNT_CAP;
    }

    public static Viewport findViewportByID(int id) {
        // TODO(ches) complete this
        return null;
    }

    public static DrawList getBackgroundDrawList(Viewport viewport) {
        return null;
    }

    public static String getClipboardText() {
        // TODO(ches) complete this
        return null;
    }

    public static int getColor(@NonNull ColorType type) {
        int result =
                switch (type) {
                    case ColorType.BORDER -> context.style.color.border;
                    case ColorType.BORDER_SHADOW -> context.style.color.borderShadow;
                    case ColorType.BUTTON -> context.style.color.button;
                    case ColorType.BUTTON_ACTIVE -> context.style.color.buttonActive;
                    case ColorType.BUTTON_HOVERED -> context.style.color.buttonHovered;
                    case ColorType.CHECK_MARK -> context.style.color.checkMark;
                    case ColorType.CHILD_BACKGROUND -> context.style.color.childBackground;
                    case ColorType.DOCKING_EMPTY_BACKGROUND ->
                            context.style.color.dockingEmptyBackground;
                    case ColorType.DOCKING_PREVIEW -> context.style.color.dockingPreview;
                    case ColorType.DRAG_DROP_TARGET -> context.style.color.dragDropTarget;
                    case ColorType.FRAME_BACKGROUND -> context.style.color.frameBackground;
                    case ColorType.FRAME_BACKGROUND_ACTIVE ->
                            context.style.color.frameBackgroundActive;
                    case ColorType.FRAME_BACKGROUND_HOVERED ->
                            context.style.color.frameBackgroundHovered;
                    case ColorType.HEADER -> context.style.color.header;
                    case ColorType.HEADER_ACTIVE -> context.style.color.headerActive;
                    case ColorType.HEADER_HOVERED -> context.style.color.headerHovered;
                    case ColorType.MENU_BAR_BACKGROUND -> context.style.color.menuBarBackground;
                    case ColorType.MODAL_WINDOWING_DIM_BACKGROUND ->
                            context.style.color.modalWindowingDimBackground;
                    case ColorType.NAV_HIGHLIGHT -> context.style.color.navHighlight;
                    case ColorType.NAV_WINDOWING_DIM_BACKGROUND ->
                            context.style.color.navWindowingDimBackground;
                    case ColorType.NAV_WINDOWING_HIGHLIGHT ->
                            context.style.color.navWindowingHighlight;
                    case ColorType.PLOT_HISTOGRAM -> context.style.color.plotHistogram;
                    case ColorType.PLOT_HISTOGRAM_HOVERED ->
                            context.style.color.plotHistogramHovered;
                    case ColorType.PLOT_LINES -> context.style.color.plotLines;
                    case ColorType.PLOT_LINES_HOVERED -> context.style.color.plotLinesHovered;
                    case ColorType.POPUP_BACKGROUND -> context.style.color.popupBackground;
                    case ColorType.RESIZE_GRIP -> context.style.color.resizeGrip;
                    case ColorType.RESIZE_GRIP_ACTIVE -> context.style.color.resizeGripActive;
                    case ColorType.RESIZE_GRIP_HOVERED -> context.style.color.resizeGripHovered;
                    case ColorType.SCROLLBAR_BACKGROUND -> context.style.color.scrollbarBackground;
                    case ColorType.SCROLLBAR_GRAB -> context.style.color.scrollbarGrab;
                    case ColorType.SCROLLBAR_GRAB_ACTIVE -> context.style.color.scrollbarGrabActive;
                    case ColorType.SCROLLBAR_GRAB_HOVERED ->
                            context.style.color.scrollbarGrabHovered;
                    case ColorType.SEPARATOR -> context.style.color.separator;
                    case ColorType.SEPARATOR_ACTIVE -> context.style.color.separatorActive;
                    case ColorType.SEPARATOR_HOVERED -> context.style.color.separatorHovered;
                    case ColorType.SLIDER_GRAB -> context.style.color.sliderGrab;
                    case ColorType.SLIDER_GRAB_ACTIVE -> context.style.color.sliderGrabActive;
                    case ColorType.TAB -> context.style.color.tab;
                    case ColorType.TABLE_BORDER_LIGHT -> context.style.color.tableBorderLight;
                    case ColorType.TABLE_BORDER_STRONG -> context.style.color.tableBorderStrong;
                    case ColorType.TABLE_HEADER_BACKGROUND ->
                            context.style.color.tableHeaderBackground;
                    case ColorType.TABLE_ROW_BACKGROUND -> context.style.color.tableRowBackground;
                    case ColorType.TABLE_ROW_BACKGROUND_ALT ->
                            context.style.color.tableRowBackgroundAlt;
                    case ColorType.TAB_DIMMED -> context.style.color.tabDimmed;
                    case ColorType.TAB_DIMMED_SELECTED -> context.style.color.tabDimmedSelected;
                    case ColorType.TAB_DIMMED_SELECTED_OVERLINE ->
                            context.style.color.tabDimmedSelectedOverline;
                    case ColorType.TAB_HOVERED -> context.style.color.tabHovered;
                    case ColorType.TAB_SELECTED -> context.style.color.tabSelected;
                    case ColorType.TAB_SELECTED_OVERLINE -> context.style.color.tabSelectedOverline;
                    case ColorType.TEXT -> context.style.color.text;
                    case ColorType.TEXT_DISABLED -> context.style.color.textDisabled;
                    case ColorType.TEXT_SELECTED_BACKGROUND ->
                            context.style.color.textSelectedBackground;
                    case ColorType.TITLE_BACKGROUND -> context.style.color.titleBackground;
                    case ColorType.TITLE_BACKGROUND_ACTIVE ->
                            context.style.color.titleBackgroundActive;
                    case ColorType.TITLE_BACKGROUND_COLLAPSED ->
                            context.style.color.titleBackgroundCollapsed;
                    case ColorType.WINDOW_BACKGROUND -> context.style.color.windowBackground;
                };

        for (var iterator = context.colorStack.descendingIterator(); iterator.hasNext(); ) {
            ColorMod mod = iterator.next();
            if (mod.type() == type) {
                result = mod.color();
                break;
            }
        }

        return result;
    }

    public static int getColor(@NonNull ColorType type, float alphaMultiplier) {
        int color = getColor(type);
        return Color.multiplyAlpha(color, alphaMultiplier);
    }

    public static int getColorWithGlobalAlpha(@NonNull ColorType styleColor) {
        int color = getColor(styleColor);
        return applyGlobalAlpha(color, false);
    }

    public static int getColorWithGlobalAlpha(@NonNull ColorType styleColor, boolean disabled) {
        int color = getColor(styleColor);
        return applyGlobalAlpha(color, disabled);
    }

    public static Vector2f getContentRegionAvailable() {
        Vector2f region = new Vector2f();
        getContentRegionAvailable(region);
        return region;
    }

    public static void getContentRegionAvailable(@NonNull Vector2f region) {
        // TODO(ches) complete this
    }

    public static Vector2f getContentRegionAvailableMax() {
        Vector2f region = new Vector2f();
        getContentRegionAvailableMax(region);
        return region;
    }

    public static void getContentRegionAvailableMax(@NonNull Vector2f region) {
        // TODO(ches) complete this
    }

    public static float getContentRegionAvailableMaxX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableMaxY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getContentRegionAvailableMin() {
        Vector2f region = new Vector2f();
        getContentRegionAvailableMin(region);
        return region;
    }

    public static void getContentRegionAvailableMin(@NonNull Vector2f region) {
        // TODO(ches) complete this
    }

    public static float getContentRegionAvailableMinX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableMinY() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getContentRegionAvailableY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getCursorPos() {
        Vector2f pos = new Vector2f();
        getCursorPos(pos);
        return pos;
    }

    public static void getCursorPos(@NonNull Vector2f pos) {
        // TODO(ches) complete this
    }

    public static float getCursorPosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getCursorPosY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getCursorScreenPos() {
        Vector2f pos = new Vector2f();
        getCursorScreenPos(pos);
        return pos;
    }

    public static void getCursorScreenPos(@NonNull Vector2f pos) {
        // TODO(ches) complete this
    }

    public static float getCursorScreenPosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getCursorScreenPosY() {
        // TODO(ches) complete this
        return 0;
    }

    public static Vector2f getCursorStartPos() {
        Vector2f pos = new Vector2f();
        getCursorStartPos(pos);
        return pos;
    }

    public static void getCursorStartPos(@NonNull Vector2f pos) {
        // TODO(ches) complete this
    }

    public static float getCursorStartPosX() {
        // TODO(ches) complete this
        return 0;
    }

    public static float getCursorStartPosY() {
        // TODO(ches) complete this
        return 0;
    }

    public static DrawList getForegroundDrawList(Viewport viewport) {
        // TODO(ches) complete this
        return null;
    }

    public static int getFrameCount() {
        // TODO(ches) complete this
        return 0;
    }

    public static int getMouseCursor() {
        // TODO(ches) complete this
        return 0;
    }

    public static void getMouseDragDelta(
            Vector2f output, @NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
    }

    public static float getMouseDragDeltaX(@NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
        return 0;
    }

    public static float getMouseDragDeltaY(@NonNull MouseButton button, float lockThreshold) {
        // TODO(ches) complete this
        return 0;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplUtils() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
