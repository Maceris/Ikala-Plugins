package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.MathUtil;

import lombok.NonNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * The colors of a style, in RGBA format. Use {@link #get(ColorType)} and {@link #set(ColorType,
 * int)} to access them by type, or the fields directly.
 */
public class StyleColors {
    public int border;
    public int borderShadow;
    public int button;
    public int buttonActive;
    public int buttonHovered;
    public int checkMark;
    public int checkboxSelectedBackground;
    public int childBackground;
    public int dockingEmptyBackground;
    public int dockingPreview;
    public int dragDropTarget;
    public int dragDropTargetBackground;
    public int frameBackground;
    public int frameBackgroundActive;
    public int frameBackgroundHovered;
    public int header;
    public int headerActive;
    public int headerHovered;
    public int inputTextCursor;
    public int menuBarBackground;
    public int modalWindowingDimBackground;
    public int navCursor;
    public int navWindowingDimBackground;
    public int navWindowingHighlight;
    public int plotHistogram;
    public int plotHistogramHovered;
    public int plotLines;
    public int plotLinesHovered;
    public int popupBackground;
    public int resizeGrip;
    public int resizeGripActive;
    public int resizeGripHovered;
    public int scrollbarBackground;
    public int scrollbarGrab;
    public int scrollbarGrabActive;
    public int scrollbarGrabHovered;
    public int separator;
    public int separatorActive;
    public int separatorHovered;
    public int sliderGrab;
    public int sliderGrabActive;
    public int tab;
    public int tableBorderLight;
    public int tableBorderStrong;
    public int tableHeaderBackground;
    public int tableRowBackground;
    public int tableRowBackgroundAlt;
    public int tabDimmed;
    public int tabDimmedSelected;
    public int tabDimmedSelectedOverline;
    public int tabHovered;
    public int tabSelected;
    public int tabSelectedOverline;
    public int text;
    public int textDisabled;
    public int textLink;
    public int textSelectedBackground;
    public int titleBackground;
    public int titleBackgroundActive;
    public int titleBackgroundCollapsed;
    public int treeLines;
    public int unsavedMarker;
    public int windowBackground;

    /** Create colors with the dark theme. */
    public StyleColors() {
        setThemeDark(this);
    }

    /**
     * Create a copy of other colors.
     *
     * @param other The colors to copy.
     */
    public StyleColors(@NonNull StyleColors other) {
        set(other);
    }

    /**
     * Fetch a color by type.
     *
     * @param type The type of color.
     * @return The color, in RGBA format.
     */
    public int get(@NonNull ColorType type) {
        return switch (type) {
            case BORDER -> border;
            case BORDER_SHADOW -> borderShadow;
            case BUTTON -> button;
            case BUTTON_ACTIVE -> buttonActive;
            case BUTTON_HOVERED -> buttonHovered;
            case CHECK_MARK -> checkMark;
            case CHECKBOX_SELECTED_BACKGROUND -> checkboxSelectedBackground;
            case CHILD_BACKGROUND -> childBackground;
            case DOCKING_EMPTY_BACKGROUND -> dockingEmptyBackground;
            case DOCKING_PREVIEW -> dockingPreview;
            case DRAG_DROP_TARGET -> dragDropTarget;
            case DRAG_DROP_TARGET_BACKGROUND -> dragDropTargetBackground;
            case FRAME_BACKGROUND -> frameBackground;
            case FRAME_BACKGROUND_ACTIVE -> frameBackgroundActive;
            case FRAME_BACKGROUND_HOVERED -> frameBackgroundHovered;
            case HEADER -> header;
            case HEADER_ACTIVE -> headerActive;
            case HEADER_HOVERED -> headerHovered;
            case INPUT_TEXT_CURSOR -> inputTextCursor;
            case MENU_BAR_BACKGROUND -> menuBarBackground;
            case MODAL_WINDOWING_DIM_BACKGROUND -> modalWindowingDimBackground;
            case NAV_CURSOR -> navCursor;
            case NAV_WINDOWING_DIM_BACKGROUND -> navWindowingDimBackground;
            case NAV_WINDOWING_HIGHLIGHT -> navWindowingHighlight;
            case PLOT_HISTOGRAM -> plotHistogram;
            case PLOT_HISTOGRAM_HOVERED -> plotHistogramHovered;
            case PLOT_LINES -> plotLines;
            case PLOT_LINES_HOVERED -> plotLinesHovered;
            case POPUP_BACKGROUND -> popupBackground;
            case RESIZE_GRIP -> resizeGrip;
            case RESIZE_GRIP_ACTIVE -> resizeGripActive;
            case RESIZE_GRIP_HOVERED -> resizeGripHovered;
            case SCROLLBAR_BACKGROUND -> scrollbarBackground;
            case SCROLLBAR_GRAB -> scrollbarGrab;
            case SCROLLBAR_GRAB_ACTIVE -> scrollbarGrabActive;
            case SCROLLBAR_GRAB_HOVERED -> scrollbarGrabHovered;
            case SEPARATOR -> separator;
            case SEPARATOR_ACTIVE -> separatorActive;
            case SEPARATOR_HOVERED -> separatorHovered;
            case SLIDER_GRAB -> sliderGrab;
            case SLIDER_GRAB_ACTIVE -> sliderGrabActive;
            case TAB -> tab;
            case TABLE_BORDER_LIGHT -> tableBorderLight;
            case TABLE_BORDER_STRONG -> tableBorderStrong;
            case TABLE_HEADER_BACKGROUND -> tableHeaderBackground;
            case TABLE_ROW_BACKGROUND -> tableRowBackground;
            case TABLE_ROW_BACKGROUND_ALT -> tableRowBackgroundAlt;
            case TAB_DIMMED -> tabDimmed;
            case TAB_DIMMED_SELECTED -> tabDimmedSelected;
            case TAB_DIMMED_SELECTED_OVERLINE -> tabDimmedSelectedOverline;
            case TAB_HOVERED -> tabHovered;
            case TAB_SELECTED -> tabSelected;
            case TAB_SELECTED_OVERLINE -> tabSelectedOverline;
            case TEXT -> text;
            case TEXT_DISABLED -> textDisabled;
            case TEXT_LINK -> textLink;
            case TEXT_SELECTED_BACKGROUND -> textSelectedBackground;
            case TITLE_BACKGROUND -> titleBackground;
            case TITLE_BACKGROUND_ACTIVE -> titleBackgroundActive;
            case TITLE_BACKGROUND_COLLAPSED -> titleBackgroundCollapsed;
            case TREE_LINES -> treeLines;
            case UNSAVED_MARKER -> unsavedMarker;
            case WINDOW_BACKGROUND -> windowBackground;
        };
    }

    /**
     * Change a color by type.
     *
     * @param type The type of color.
     * @param color The color, in RGBA format.
     */
    public void set(@NonNull ColorType type, int color) {
        switch (type) {
            case BORDER -> border = color;
            case BORDER_SHADOW -> borderShadow = color;
            case BUTTON -> button = color;
            case BUTTON_ACTIVE -> buttonActive = color;
            case BUTTON_HOVERED -> buttonHovered = color;
            case CHECK_MARK -> checkMark = color;
            case CHECKBOX_SELECTED_BACKGROUND -> checkboxSelectedBackground = color;
            case CHILD_BACKGROUND -> childBackground = color;
            case DOCKING_EMPTY_BACKGROUND -> dockingEmptyBackground = color;
            case DOCKING_PREVIEW -> dockingPreview = color;
            case DRAG_DROP_TARGET -> dragDropTarget = color;
            case DRAG_DROP_TARGET_BACKGROUND -> dragDropTargetBackground = color;
            case FRAME_BACKGROUND -> frameBackground = color;
            case FRAME_BACKGROUND_ACTIVE -> frameBackgroundActive = color;
            case FRAME_BACKGROUND_HOVERED -> frameBackgroundHovered = color;
            case HEADER -> header = color;
            case HEADER_ACTIVE -> headerActive = color;
            case HEADER_HOVERED -> headerHovered = color;
            case INPUT_TEXT_CURSOR -> inputTextCursor = color;
            case MENU_BAR_BACKGROUND -> menuBarBackground = color;
            case MODAL_WINDOWING_DIM_BACKGROUND -> modalWindowingDimBackground = color;
            case NAV_CURSOR -> navCursor = color;
            case NAV_WINDOWING_DIM_BACKGROUND -> navWindowingDimBackground = color;
            case NAV_WINDOWING_HIGHLIGHT -> navWindowingHighlight = color;
            case PLOT_HISTOGRAM -> plotHistogram = color;
            case PLOT_HISTOGRAM_HOVERED -> plotHistogramHovered = color;
            case PLOT_LINES -> plotLines = color;
            case PLOT_LINES_HOVERED -> plotLinesHovered = color;
            case POPUP_BACKGROUND -> popupBackground = color;
            case RESIZE_GRIP -> resizeGrip = color;
            case RESIZE_GRIP_ACTIVE -> resizeGripActive = color;
            case RESIZE_GRIP_HOVERED -> resizeGripHovered = color;
            case SCROLLBAR_BACKGROUND -> scrollbarBackground = color;
            case SCROLLBAR_GRAB -> scrollbarGrab = color;
            case SCROLLBAR_GRAB_ACTIVE -> scrollbarGrabActive = color;
            case SCROLLBAR_GRAB_HOVERED -> scrollbarGrabHovered = color;
            case SEPARATOR -> separator = color;
            case SEPARATOR_ACTIVE -> separatorActive = color;
            case SEPARATOR_HOVERED -> separatorHovered = color;
            case SLIDER_GRAB -> sliderGrab = color;
            case SLIDER_GRAB_ACTIVE -> sliderGrabActive = color;
            case TAB -> tab = color;
            case TABLE_BORDER_LIGHT -> tableBorderLight = color;
            case TABLE_BORDER_STRONG -> tableBorderStrong = color;
            case TABLE_HEADER_BACKGROUND -> tableHeaderBackground = color;
            case TABLE_ROW_BACKGROUND -> tableRowBackground = color;
            case TABLE_ROW_BACKGROUND_ALT -> tableRowBackgroundAlt = color;
            case TAB_DIMMED -> tabDimmed = color;
            case TAB_DIMMED_SELECTED -> tabDimmedSelected = color;
            case TAB_DIMMED_SELECTED_OVERLINE -> tabDimmedSelectedOverline = color;
            case TAB_HOVERED -> tabHovered = color;
            case TAB_SELECTED -> tabSelected = color;
            case TAB_SELECTED_OVERLINE -> tabSelectedOverline = color;
            case TEXT -> text = color;
            case TEXT_DISABLED -> textDisabled = color;
            case TEXT_LINK -> textLink = color;
            case TEXT_SELECTED_BACKGROUND -> textSelectedBackground = color;
            case TITLE_BACKGROUND -> titleBackground = color;
            case TITLE_BACKGROUND_ACTIVE -> titleBackgroundActive = color;
            case TITLE_BACKGROUND_COLLAPSED -> titleBackgroundCollapsed = color;
            case TREE_LINES -> treeLines = color;
            case UNSAVED_MARKER -> unsavedMarker = color;
            case WINDOW_BACKGROUND -> windowBackground = color;
        }
    }

    /**
     * Copy all the colors from other colors.
     *
     * @param other The colors to copy.
     */
    public void set(@NonNull StyleColors other) {
        for (ColorType type : ColorType.values()) {
            set(type, other.get(type));
        }
    }

    /**
     * Create a color from floating point components.
     *
     * @param r Red, from 0 to 1.
     * @param g Green, from 0 to 1.
     * @param b Blue, from 0 to 1.
     * @param a Alpha, from 0 to 1.
     * @return The components.
     */
    private static float[] vec(float r, float g, float b, float a) {
        return new float[] {r, g, b, a};
    }

    /**
     * Linearly interpolate between two colors.
     *
     * @param from The color at t = 0.
     * @param to The color at t = 1.
     * @param t How far to interpolate.
     * @return The interpolated color.
     */
    private static float[] lerp(float[] from, float[] to, float t) {
        final float[] result = new float[4];
        for (int i = 0; i < 4; ++i) {
            result[i] = from[i] + (to[i] - from[i]) * t;
        }
        return result;
    }

    /**
     * Multiply two colors component by component.
     *
     * @param a The first color.
     * @param b The second color.
     * @return The product.
     */
    private static float[] multiply(float[] a, float[] b) {
        return vec(a[0] * b[0], a[1] * b[1], a[2] * b[2], a[3] * b[3]);
    }

    /**
     * Convert a floating point component to a byte, rounding and saturating like Dear ImGui.
     *
     * @param value The component, from 0 to 1.
     * @return The component, from 0 to 255.
     */
    private static int toByte(float value) {
        return (int) (MathUtil.clamp(value, 0.0f, 1.0f) * 255.0f + 0.5f);
    }

    /**
     * Store theme colors into a target.
     *
     * @param target The colors to modify.
     * @param c The theme colors, as floating point components.
     */
    private static void apply(@NonNull StyleColors target, @NonNull Map<ColorType, float[]> c) {
        for (Map.Entry<ColorType, float[]> entry : c.entrySet()) {
            final float[] v = entry.getValue();
            target.set(
                    entry.getKey(),
                    Color.rgba(toByte(v[0]), toByte(v[1]), toByte(v[2]), toByte(v[3])));
        }
    }

    /**
     * Set the colors to the dark theme, which is the default.
     *
     * @param target The colors to modify.
     */
    public static void setThemeDark(@NonNull StyleColors target) {
        final Map<ColorType, float[]> c = new EnumMap<>(ColorType.class);
        c.put(ColorType.TEXT, vec(1.00f, 1.00f, 1.00f, 1.00f));
        c.put(ColorType.TEXT_DISABLED, vec(0.50f, 0.50f, 0.50f, 1.00f));
        c.put(ColorType.WINDOW_BACKGROUND, vec(0.06f, 0.06f, 0.06f, 0.94f));
        c.put(ColorType.CHILD_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.POPUP_BACKGROUND, vec(0.08f, 0.08f, 0.08f, 0.94f));
        c.put(ColorType.BORDER, vec(0.43f, 0.43f, 0.50f, 0.50f));
        c.put(ColorType.BORDER_SHADOW, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.FRAME_BACKGROUND, vec(0.16f, 0.29f, 0.48f, 0.54f));
        c.put(ColorType.FRAME_BACKGROUND_HOVERED, vec(0.26f, 0.59f, 0.98f, 0.40f));
        c.put(ColorType.FRAME_BACKGROUND_ACTIVE, vec(0.26f, 0.59f, 0.98f, 0.67f));
        c.put(ColorType.TITLE_BACKGROUND, vec(0.04f, 0.04f, 0.04f, 1.00f));
        c.put(ColorType.TITLE_BACKGROUND_ACTIVE, vec(0.16f, 0.29f, 0.48f, 1.00f));
        c.put(ColorType.TITLE_BACKGROUND_COLLAPSED, vec(0.00f, 0.00f, 0.00f, 0.51f));
        c.put(ColorType.MENU_BAR_BACKGROUND, vec(0.14f, 0.14f, 0.14f, 1.00f));
        c.put(ColorType.SCROLLBAR_BACKGROUND, vec(0.02f, 0.02f, 0.02f, 0.53f));
        c.put(ColorType.SCROLLBAR_GRAB, vec(0.31f, 0.31f, 0.31f, 1.00f));
        c.put(ColorType.SCROLLBAR_GRAB_HOVERED, vec(0.41f, 0.41f, 0.41f, 1.00f));
        c.put(ColorType.SCROLLBAR_GRAB_ACTIVE, vec(0.51f, 0.51f, 0.51f, 1.00f));
        c.put(ColorType.CHECK_MARK, vec(0.26f, 0.59f, 0.98f, 1.00f));
        c.put(
                ColorType.CHECKBOX_SELECTED_BACKGROUND,
                lerp(
                        c.get(ColorType.FRAME_BACKGROUND),
                        c.get(ColorType.FRAME_BACKGROUND_HOVERED),
                        0.65f));
        c.put(ColorType.SLIDER_GRAB, vec(0.24f, 0.52f, 0.88f, 1.00f));
        c.put(ColorType.SLIDER_GRAB_ACTIVE, vec(0.26f, 0.59f, 0.98f, 1.00f));
        c.put(ColorType.BUTTON, vec(0.26f, 0.59f, 0.98f, 0.40f));
        c.put(ColorType.BUTTON_HOVERED, vec(0.26f, 0.59f, 0.98f, 1.00f));
        c.put(ColorType.BUTTON_ACTIVE, vec(0.06f, 0.53f, 0.98f, 1.00f));
        c.put(ColorType.HEADER, vec(0.26f, 0.59f, 0.98f, 0.31f));
        c.put(ColorType.HEADER_HOVERED, vec(0.26f, 0.59f, 0.98f, 0.80f));
        c.put(ColorType.HEADER_ACTIVE, vec(0.26f, 0.59f, 0.98f, 1.00f));
        c.put(ColorType.SEPARATOR, c.get(ColorType.BORDER));
        c.put(ColorType.SEPARATOR_HOVERED, vec(0.10f, 0.40f, 0.75f, 0.78f));
        c.put(ColorType.SEPARATOR_ACTIVE, vec(0.10f, 0.40f, 0.75f, 1.00f));
        c.put(ColorType.RESIZE_GRIP, vec(0.26f, 0.59f, 0.98f, 0.20f));
        c.put(ColorType.RESIZE_GRIP_HOVERED, vec(0.26f, 0.59f, 0.98f, 0.67f));
        c.put(ColorType.RESIZE_GRIP_ACTIVE, vec(0.26f, 0.59f, 0.98f, 0.95f));
        c.put(ColorType.INPUT_TEXT_CURSOR, c.get(ColorType.TEXT));
        c.put(ColorType.TAB_HOVERED, c.get(ColorType.HEADER_HOVERED));
        c.put(
                ColorType.TAB,
                lerp(c.get(ColorType.HEADER), c.get(ColorType.TITLE_BACKGROUND_ACTIVE), 0.80f));
        c.put(
                ColorType.TAB_SELECTED,
                lerp(
                        c.get(ColorType.HEADER_ACTIVE),
                        c.get(ColorType.TITLE_BACKGROUND_ACTIVE),
                        0.60f));
        c.put(ColorType.TAB_SELECTED_OVERLINE, c.get(ColorType.HEADER_ACTIVE));
        c.put(
                ColorType.TAB_DIMMED,
                lerp(c.get(ColorType.TAB), c.get(ColorType.TITLE_BACKGROUND), 0.80f));
        c.put(
                ColorType.TAB_DIMMED_SELECTED,
                lerp(c.get(ColorType.TAB_SELECTED), c.get(ColorType.TITLE_BACKGROUND), 0.40f));
        c.put(ColorType.TAB_DIMMED_SELECTED_OVERLINE, vec(0.50f, 0.50f, 0.50f, 0.00f));
        c.put(
                ColorType.DOCKING_PREVIEW,
                multiply(c.get(ColorType.HEADER_ACTIVE), vec(1.0f, 1.0f, 1.0f, 0.7f)));
        c.put(ColorType.DOCKING_EMPTY_BACKGROUND, vec(0.20f, 0.20f, 0.20f, 1.00f));
        c.put(ColorType.PLOT_LINES, vec(0.61f, 0.61f, 0.61f, 1.00f));
        c.put(ColorType.PLOT_LINES_HOVERED, vec(1.00f, 0.43f, 0.35f, 1.00f));
        c.put(ColorType.PLOT_HISTOGRAM, vec(0.90f, 0.70f, 0.00f, 1.00f));
        c.put(ColorType.PLOT_HISTOGRAM_HOVERED, vec(1.00f, 0.60f, 0.00f, 1.00f));
        c.put(ColorType.TABLE_HEADER_BACKGROUND, vec(0.19f, 0.19f, 0.20f, 1.00f));
        c.put(ColorType.TABLE_BORDER_STRONG, vec(0.31f, 0.31f, 0.35f, 1.00f));
        c.put(ColorType.TABLE_BORDER_LIGHT, vec(0.23f, 0.23f, 0.25f, 1.00f));
        c.put(ColorType.TABLE_ROW_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.TABLE_ROW_BACKGROUND_ALT, vec(1.00f, 1.00f, 1.00f, 0.06f));
        c.put(ColorType.TEXT_LINK, c.get(ColorType.HEADER_ACTIVE));
        c.put(ColorType.TEXT_SELECTED_BACKGROUND, vec(0.26f, 0.59f, 0.98f, 0.35f));
        c.put(ColorType.TREE_LINES, c.get(ColorType.BORDER));
        c.put(ColorType.DRAG_DROP_TARGET, vec(1.00f, 1.00f, 0.00f, 0.90f));
        c.put(ColorType.DRAG_DROP_TARGET_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.UNSAVED_MARKER, vec(1.00f, 1.00f, 1.00f, 1.00f));
        c.put(ColorType.NAV_CURSOR, vec(0.26f, 0.59f, 0.98f, 1.00f));
        c.put(ColorType.NAV_WINDOWING_HIGHLIGHT, vec(1.00f, 1.00f, 1.00f, 0.70f));
        c.put(ColorType.NAV_WINDOWING_DIM_BACKGROUND, vec(0.80f, 0.80f, 0.80f, 0.20f));
        c.put(ColorType.MODAL_WINDOWING_DIM_BACKGROUND, vec(0.80f, 0.80f, 0.80f, 0.35f));
        apply(target, c);
    }

    /**
     * Set the colors to the classic theme.
     *
     * @param target The colors to modify.
     */
    public static void setThemeClassic(@NonNull StyleColors target) {
        final Map<ColorType, float[]> c = new EnumMap<>(ColorType.class);
        c.put(ColorType.TEXT, vec(0.90f, 0.90f, 0.90f, 1.00f));
        c.put(ColorType.TEXT_DISABLED, vec(0.60f, 0.60f, 0.60f, 1.00f));
        c.put(ColorType.WINDOW_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.85f));
        c.put(ColorType.CHILD_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.POPUP_BACKGROUND, vec(0.11f, 0.11f, 0.14f, 0.92f));
        c.put(ColorType.BORDER, vec(0.50f, 0.50f, 0.50f, 0.50f));
        c.put(ColorType.BORDER_SHADOW, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.FRAME_BACKGROUND, vec(0.43f, 0.43f, 0.43f, 0.39f));
        c.put(ColorType.FRAME_BACKGROUND_HOVERED, vec(0.47f, 0.47f, 0.69f, 0.40f));
        c.put(ColorType.FRAME_BACKGROUND_ACTIVE, vec(0.42f, 0.41f, 0.64f, 0.69f));
        c.put(ColorType.TITLE_BACKGROUND, vec(0.27f, 0.27f, 0.54f, 0.83f));
        c.put(ColorType.TITLE_BACKGROUND_ACTIVE, vec(0.32f, 0.32f, 0.63f, 0.87f));
        c.put(ColorType.TITLE_BACKGROUND_COLLAPSED, vec(0.40f, 0.40f, 0.80f, 0.20f));
        c.put(ColorType.MENU_BAR_BACKGROUND, vec(0.40f, 0.40f, 0.55f, 0.80f));
        c.put(ColorType.SCROLLBAR_BACKGROUND, vec(0.20f, 0.25f, 0.30f, 0.60f));
        c.put(ColorType.SCROLLBAR_GRAB, vec(0.40f, 0.40f, 0.80f, 0.30f));
        c.put(ColorType.SCROLLBAR_GRAB_HOVERED, vec(0.40f, 0.40f, 0.80f, 0.40f));
        c.put(ColorType.SCROLLBAR_GRAB_ACTIVE, vec(0.41f, 0.39f, 0.80f, 0.60f));
        c.put(ColorType.CHECK_MARK, vec(0.90f, 0.90f, 0.90f, 0.50f));
        c.put(
                ColorType.CHECKBOX_SELECTED_BACKGROUND,
                lerp(
                        c.get(ColorType.FRAME_BACKGROUND),
                        c.get(ColorType.FRAME_BACKGROUND_ACTIVE),
                        0.65f));
        c.put(ColorType.SLIDER_GRAB, vec(1.00f, 1.00f, 1.00f, 0.30f));
        c.put(ColorType.SLIDER_GRAB_ACTIVE, vec(0.41f, 0.39f, 0.80f, 0.60f));
        c.put(ColorType.BUTTON, vec(0.35f, 0.40f, 0.61f, 0.62f));
        c.put(ColorType.BUTTON_HOVERED, vec(0.40f, 0.48f, 0.71f, 0.79f));
        c.put(ColorType.BUTTON_ACTIVE, vec(0.46f, 0.54f, 0.80f, 1.00f));
        c.put(ColorType.HEADER, vec(0.40f, 0.40f, 0.90f, 0.45f));
        c.put(ColorType.HEADER_HOVERED, vec(0.45f, 0.45f, 0.90f, 0.80f));
        c.put(ColorType.HEADER_ACTIVE, vec(0.53f, 0.53f, 0.87f, 0.80f));
        c.put(ColorType.SEPARATOR, vec(0.50f, 0.50f, 0.50f, 0.60f));
        c.put(ColorType.SEPARATOR_HOVERED, vec(0.60f, 0.60f, 0.70f, 1.00f));
        c.put(ColorType.SEPARATOR_ACTIVE, vec(0.70f, 0.70f, 0.90f, 1.00f));
        c.put(ColorType.RESIZE_GRIP, vec(1.00f, 1.00f, 1.00f, 0.10f));
        c.put(ColorType.RESIZE_GRIP_HOVERED, vec(0.78f, 0.82f, 1.00f, 0.60f));
        c.put(ColorType.RESIZE_GRIP_ACTIVE, vec(0.78f, 0.82f, 1.00f, 0.90f));
        c.put(ColorType.INPUT_TEXT_CURSOR, c.get(ColorType.TEXT));
        c.put(ColorType.TAB_HOVERED, c.get(ColorType.HEADER_HOVERED));
        c.put(
                ColorType.TAB,
                lerp(c.get(ColorType.HEADER), c.get(ColorType.TITLE_BACKGROUND_ACTIVE), 0.80f));
        c.put(
                ColorType.TAB_SELECTED,
                lerp(
                        c.get(ColorType.HEADER_ACTIVE),
                        c.get(ColorType.TITLE_BACKGROUND_ACTIVE),
                        0.60f));
        c.put(ColorType.TAB_SELECTED_OVERLINE, c.get(ColorType.HEADER_ACTIVE));
        c.put(
                ColorType.TAB_DIMMED,
                lerp(c.get(ColorType.TAB), c.get(ColorType.TITLE_BACKGROUND), 0.80f));
        c.put(
                ColorType.TAB_DIMMED_SELECTED,
                lerp(c.get(ColorType.TAB_SELECTED), c.get(ColorType.TITLE_BACKGROUND), 0.40f));
        c.put(ColorType.TAB_DIMMED_SELECTED_OVERLINE, vec(0.53f, 0.53f, 0.87f, 0.00f));
        c.put(
                ColorType.DOCKING_PREVIEW,
                multiply(c.get(ColorType.HEADER), vec(1.0f, 1.0f, 1.0f, 0.7f)));
        c.put(ColorType.DOCKING_EMPTY_BACKGROUND, vec(0.20f, 0.20f, 0.20f, 1.00f));
        c.put(ColorType.PLOT_LINES, vec(1.00f, 1.00f, 1.00f, 1.00f));
        c.put(ColorType.PLOT_LINES_HOVERED, vec(0.90f, 0.70f, 0.00f, 1.00f));
        c.put(ColorType.PLOT_HISTOGRAM, vec(0.90f, 0.70f, 0.00f, 1.00f));
        c.put(ColorType.PLOT_HISTOGRAM_HOVERED, vec(1.00f, 0.60f, 0.00f, 1.00f));
        c.put(ColorType.TABLE_HEADER_BACKGROUND, vec(0.27f, 0.27f, 0.38f, 1.00f));
        c.put(ColorType.TABLE_BORDER_STRONG, vec(0.31f, 0.31f, 0.45f, 1.00f));
        c.put(ColorType.TABLE_BORDER_LIGHT, vec(0.26f, 0.26f, 0.28f, 1.00f));
        c.put(ColorType.TABLE_ROW_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.TABLE_ROW_BACKGROUND_ALT, vec(1.00f, 1.00f, 1.00f, 0.07f));
        c.put(ColorType.TEXT_LINK, c.get(ColorType.HEADER_ACTIVE));
        c.put(ColorType.TEXT_SELECTED_BACKGROUND, vec(0.00f, 0.00f, 1.00f, 0.35f));
        c.put(ColorType.TREE_LINES, c.get(ColorType.BORDER));
        c.put(ColorType.DRAG_DROP_TARGET, vec(1.00f, 1.00f, 0.00f, 0.90f));
        c.put(ColorType.DRAG_DROP_TARGET_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.UNSAVED_MARKER, vec(0.90f, 0.90f, 0.90f, 1.00f));
        c.put(ColorType.NAV_CURSOR, c.get(ColorType.HEADER_HOVERED));
        c.put(ColorType.NAV_WINDOWING_HIGHLIGHT, vec(1.00f, 1.00f, 1.00f, 0.70f));
        c.put(ColorType.NAV_WINDOWING_DIM_BACKGROUND, vec(0.80f, 0.80f, 0.80f, 0.20f));
        c.put(ColorType.MODAL_WINDOWING_DIM_BACKGROUND, vec(0.20f, 0.20f, 0.20f, 0.35f));
        apply(target, c);
    }

    /**
     * Set the colors to the light theme, which is better suited to a thicker font than the default
     * and frame borders.
     *
     * @param target The colors to modify.
     */
    public static void setThemeLight(@NonNull StyleColors target) {
        final Map<ColorType, float[]> c = new EnumMap<>(ColorType.class);
        c.put(ColorType.TEXT, vec(0.00f, 0.00f, 0.00f, 1.00f));
        c.put(ColorType.TEXT_DISABLED, vec(0.60f, 0.60f, 0.60f, 1.00f));
        c.put(ColorType.WINDOW_BACKGROUND, vec(0.94f, 0.94f, 0.94f, 1.00f));
        c.put(ColorType.CHILD_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.POPUP_BACKGROUND, vec(1.00f, 1.00f, 1.00f, 0.98f));
        c.put(ColorType.BORDER, vec(0.00f, 0.00f, 0.00f, 0.30f));
        c.put(ColorType.BORDER_SHADOW, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.FRAME_BACKGROUND, vec(1.00f, 1.00f, 1.00f, 1.00f));
        c.put(ColorType.FRAME_BACKGROUND_HOVERED, vec(0.26f, 0.59f, 0.98f, 0.40f));
        c.put(ColorType.FRAME_BACKGROUND_ACTIVE, vec(0.26f, 0.59f, 0.98f, 0.67f));
        c.put(ColorType.TITLE_BACKGROUND, vec(0.82f, 0.82f, 0.82f, 1.00f));
        c.put(ColorType.TITLE_BACKGROUND_ACTIVE, vec(1.00f, 1.00f, 1.00f, 1.00f));
        c.put(ColorType.TITLE_BACKGROUND_COLLAPSED, vec(1.00f, 1.00f, 1.00f, 0.43f));
        c.put(ColorType.MENU_BAR_BACKGROUND, vec(0.86f, 0.86f, 0.86f, 1.00f));
        c.put(ColorType.SCROLLBAR_BACKGROUND, vec(0.98f, 0.98f, 0.98f, 0.53f));
        c.put(ColorType.SCROLLBAR_GRAB, vec(0.69f, 0.69f, 0.69f, 0.80f));
        c.put(ColorType.SCROLLBAR_GRAB_HOVERED, vec(0.49f, 0.49f, 0.49f, 0.80f));
        c.put(ColorType.SCROLLBAR_GRAB_ACTIVE, vec(0.49f, 0.49f, 0.49f, 1.00f));
        c.put(ColorType.CHECK_MARK, vec(0.26f, 0.59f, 0.98f, 1.00f));
        c.put(ColorType.CHECKBOX_SELECTED_BACKGROUND, vec(0.95f, 0.97f, 1.00f, 1.00f));
        c.put(ColorType.SLIDER_GRAB, vec(0.26f, 0.59f, 0.98f, 0.78f));
        c.put(ColorType.SLIDER_GRAB_ACTIVE, vec(0.46f, 0.54f, 0.80f, 0.60f));
        c.put(ColorType.BUTTON, vec(0.26f, 0.59f, 0.98f, 0.40f));
        c.put(ColorType.BUTTON_HOVERED, vec(0.26f, 0.59f, 0.98f, 1.00f));
        c.put(ColorType.BUTTON_ACTIVE, vec(0.06f, 0.53f, 0.98f, 1.00f));
        c.put(ColorType.HEADER, vec(0.26f, 0.59f, 0.98f, 0.31f));
        c.put(ColorType.HEADER_HOVERED, vec(0.26f, 0.59f, 0.98f, 0.80f));
        c.put(ColorType.HEADER_ACTIVE, vec(0.26f, 0.59f, 0.98f, 1.00f));
        c.put(ColorType.SEPARATOR, vec(0.39f, 0.39f, 0.39f, 0.62f));
        c.put(ColorType.SEPARATOR_HOVERED, vec(0.14f, 0.44f, 0.80f, 0.78f));
        c.put(ColorType.SEPARATOR_ACTIVE, vec(0.14f, 0.44f, 0.80f, 1.00f));
        c.put(ColorType.RESIZE_GRIP, vec(0.35f, 0.35f, 0.35f, 0.17f));
        c.put(ColorType.RESIZE_GRIP_HOVERED, vec(0.26f, 0.59f, 0.98f, 0.67f));
        c.put(ColorType.RESIZE_GRIP_ACTIVE, vec(0.26f, 0.59f, 0.98f, 0.95f));
        c.put(ColorType.INPUT_TEXT_CURSOR, c.get(ColorType.TEXT));
        c.put(ColorType.TAB_HOVERED, c.get(ColorType.HEADER_HOVERED));
        c.put(
                ColorType.TAB,
                lerp(c.get(ColorType.HEADER), c.get(ColorType.TITLE_BACKGROUND_ACTIVE), 0.90f));
        c.put(
                ColorType.TAB_SELECTED,
                lerp(
                        c.get(ColorType.HEADER_ACTIVE),
                        c.get(ColorType.TITLE_BACKGROUND_ACTIVE),
                        0.60f));
        c.put(ColorType.TAB_SELECTED_OVERLINE, c.get(ColorType.HEADER_ACTIVE));
        c.put(
                ColorType.TAB_DIMMED,
                lerp(c.get(ColorType.TAB), c.get(ColorType.TITLE_BACKGROUND), 0.80f));
        c.put(
                ColorType.TAB_DIMMED_SELECTED,
                lerp(c.get(ColorType.TAB_SELECTED), c.get(ColorType.TITLE_BACKGROUND), 0.40f));
        c.put(ColorType.TAB_DIMMED_SELECTED_OVERLINE, vec(0.26f, 0.59f, 1.00f, 0.00f));
        c.put(
                ColorType.DOCKING_PREVIEW,
                multiply(c.get(ColorType.HEADER), vec(1.0f, 1.0f, 1.0f, 0.7f)));
        c.put(ColorType.DOCKING_EMPTY_BACKGROUND, vec(0.68f, 0.69f, 0.73f, 1.00f));
        c.put(ColorType.PLOT_LINES, vec(0.39f, 0.39f, 0.39f, 1.00f));
        c.put(ColorType.PLOT_LINES_HOVERED, vec(1.00f, 0.43f, 0.35f, 1.00f));
        c.put(ColorType.PLOT_HISTOGRAM, vec(0.90f, 0.70f, 0.00f, 1.00f));
        c.put(ColorType.PLOT_HISTOGRAM_HOVERED, vec(1.00f, 0.45f, 0.00f, 1.00f));
        c.put(ColorType.TABLE_HEADER_BACKGROUND, vec(0.78f, 0.87f, 0.98f, 1.00f));
        c.put(ColorType.TABLE_BORDER_STRONG, vec(0.57f, 0.57f, 0.64f, 1.00f));
        c.put(ColorType.TABLE_BORDER_LIGHT, vec(0.68f, 0.68f, 0.74f, 1.00f));
        c.put(ColorType.TABLE_ROW_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.TABLE_ROW_BACKGROUND_ALT, vec(0.30f, 0.30f, 0.30f, 0.09f));
        c.put(ColorType.TEXT_LINK, c.get(ColorType.HEADER_ACTIVE));
        c.put(ColorType.TEXT_SELECTED_BACKGROUND, vec(0.26f, 0.59f, 0.98f, 0.35f));
        c.put(ColorType.TREE_LINES, c.get(ColorType.BORDER));
        c.put(ColorType.DRAG_DROP_TARGET, vec(0.26f, 0.59f, 0.98f, 0.95f));
        c.put(ColorType.DRAG_DROP_TARGET_BACKGROUND, vec(0.00f, 0.00f, 0.00f, 0.00f));
        c.put(ColorType.UNSAVED_MARKER, vec(0.00f, 0.00f, 0.00f, 1.00f));
        c.put(ColorType.NAV_CURSOR, c.get(ColorType.HEADER_HOVERED));
        c.put(ColorType.NAV_WINDOWING_HIGHLIGHT, vec(0.70f, 0.70f, 0.70f, 0.70f));
        c.put(ColorType.NAV_WINDOWING_DIM_BACKGROUND, vec(0.20f, 0.20f, 0.20f, 0.20f));
        c.put(ColorType.MODAL_WINDOWING_DIM_BACKGROUND, vec(0.20f, 0.20f, 0.20f, 0.35f));
        apply(target, c);
    }
}
