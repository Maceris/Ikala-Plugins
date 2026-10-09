package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.BoxSelectState;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DockContext;
import com.ikalagaming.graphics.gui.data.DockNode;
import com.ikalagaming.graphics.gui.data.DockNodeSettings;
import com.ikalagaming.graphics.gui.data.DrawData;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.FocusScopeData;
import com.ikalagaming.graphics.gui.data.FontAtlas;
import com.ikalagaming.graphics.gui.data.FontMetrics;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.data.IkInt;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.data.InputTextState;
import com.ikalagaming.graphics.gui.data.KeyOwnerData;
import com.ikalagaming.graphics.gui.data.KeyRoutingData;
import com.ikalagaming.graphics.gui.data.ListClipper;
import com.ikalagaming.graphics.gui.data.MetricsConfig;
import com.ikalagaming.graphics.gui.data.MultiSelectState;
import com.ikalagaming.graphics.gui.data.PlatformMonitor;
import com.ikalagaming.graphics.gui.data.PopupData;
import com.ikalagaming.graphics.gui.data.SettingsCleanupArgs;
import com.ikalagaming.graphics.gui.data.SettingsHandler;
import com.ikalagaming.graphics.gui.data.Storage;
import com.ikalagaming.graphics.gui.data.TabBar;
import com.ikalagaming.graphics.gui.data.TabItem;
import com.ikalagaming.graphics.gui.data.Table;
import com.ikalagaming.graphics.gui.data.TableColumn;
import com.ikalagaming.graphics.gui.data.TableColumnSettings;
import com.ikalagaming.graphics.gui.data.TableInstanceData;
import com.ikalagaming.graphics.gui.data.TableSettings;
import com.ikalagaming.graphics.gui.data.TypingSelectState;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.data.WindowSettings;
import com.ikalagaming.graphics.gui.enums.Axis;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.enums.SortDirection;
import com.ikalagaming.graphics.gui.flags.ChildFlags;
import com.ikalagaming.graphics.gui.flags.DockNodeFlags;
import com.ikalagaming.graphics.gui.flags.DrawFlags;
import com.ikalagaming.graphics.gui.flags.InputTextFlags;
import com.ikalagaming.graphics.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.gui.flags.TableFlags;
import com.ikalagaming.graphics.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.gui.flags.ViewportFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The metrics/debugger window, which displays the internal state of the library, and the inspectors
 * it uses for each kind of data.
 */
@Slf4j
class IkGuiImplMetrics {
    /** The shared context. */
    static Context context;

    /** Highlight color used to show what an inspector entry refers to. */
    private static final int HIGHLIGHT_COLOR = Color.rgba(255, 255, 0, 255);

    /** The names of the window rectangles that can be displayed. */
    private static final String[] WINDOW_RECT_NAMES = {
        "OuterRect",
        "OuterRectClipped",
        "InnerRect",
        "InnerClipRect",
        "WorkRect",
        "Content",
        "ContentIdeal",
        "ContentRegionRect"
    };

    private static final int WRT_OUTER_RECT = 0;
    private static final int WRT_OUTER_RECT_CLIPPED = 1;
    private static final int WRT_INNER_RECT = 2;
    private static final int WRT_INNER_CLIP_RECT = 3;
    private static final int WRT_WORK_RECT = 4;
    private static final int WRT_CONTENT = 5;
    private static final int WRT_CONTENT_IDEAL = 6;
    private static final int WRT_CONTENT_REGION_RECT = 7;

    /** The names of the table rectangles that can be displayed. */
    private static final String[] TABLE_RECT_NAMES = {
        "OuterRect",
        "InnerRect",
        "WorkRect",
        "HostClipRect",
        "InnerClipRect",
        "BackgroundClipRect",
        "ColumnsRect",
        "ColumnsWorkRect",
        "ColumnsClipRect",
        "ColumnsContentHeadersUsed",
        "ColumnsContentHeadersIdeal",
        "ColumnsContentFrozen",
        "ColumnsContentUnfrozen"
    };

    private static final int TRT_OUTER_RECT = 0;
    private static final int TRT_INNER_RECT = 1;
    private static final int TRT_WORK_RECT = 2;
    private static final int TRT_HOST_CLIP_RECT = 3;
    private static final int TRT_INNER_CLIP_RECT = 4;
    private static final int TRT_BACKGROUND_CLIP_RECT = 5;
    private static final int TRT_COLUMNS_RECT = 6;
    private static final int TRT_COLUMNS_WORK_RECT = 7;
    private static final int TRT_COLUMNS_CLIP_RECT = 8;
    private static final int TRT_COLUMNS_CONTENT_HEADERS_USED = 9;
    private static final int TRT_COLUMNS_CONTENT_HEADERS_IDEAL = 10;
    private static final int TRT_COLUMNS_CONTENT_FROZEN = 11;
    private static final int TRT_COLUMNS_CONTENT_UNFROZEN = 12;

    /** How many cached characters to list in the font section. */
    private static final int MAX_LISTED_CHARACTERS = 512;

    /** Private constructor so this is not instantiated. */
    private IkGuiImplMetrics() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * A "(?)" marker with a tooltip.
     *
     * @param description The text for the tooltip.
     */
    static void metricsHelpMarker(@NonNull String description) {
        IkGui.textDisabled("(?)");
        if (IkGui.beginItemTooltip()) {
            IkGui.pushTextWrapPos(IkGuiInternal.getFontSize() * 35.0f);
            IkGui.textUnformatted(description);
            IkGui.popTextWrapPos();
            IkGui.endTooltip();
        }
    }

    /**
     * Fetch the foreground draw list for the viewport of a window.
     *
     * @param window The window.
     * @return The foreground draw list.
     */
    private static DrawList foregroundDrawList(Window window) {
        return IkGuiImplUtils.getForegroundDrawList(
                window != null && window.viewport != null
                        ? window.viewport
                        : IkGuiImplViewports.getMainViewport());
    }

    /**
     * Draw a highlight rectangle on the foreground of a window's viewport.
     *
     * @param window The window whose viewport to draw on.
     * @param rect The rectangle.
     */
    private static void highlight(Window window, @NonNull RectFloat rect) {
        foregroundDrawList(window)
                .addRect(
                        rect.getLeft(),
                        rect.getTop(),
                        rect.getRight(),
                        rect.getBottom(),
                        HIGHLIGHT_COLOR);
    }

    /**
     * Format a rectangle for display.
     *
     * @param rect The rectangle.
     * @return The formatted rectangle.
     */
    private static String formatRect(@NonNull RectFloat rect) {
        return String.format(
                "(%6.1f,%6.1f) (%6.1f,%6.1f) Size (%6.1f,%6.1f)",
                rect.getLeft(),
                rect.getTop(),
                rect.getRight(),
                rect.getBottom(),
                rect.getWidth(),
                rect.getHeight());
    }

    /**
     * Push the disabled text color if something is inactive.
     *
     * @param active Whether the thing is active.
     */
    private static void pushInactiveColor(boolean active) {
        if (!active) {
            IkGui.pushStyleColor(ColorType.TEXT, IkGuiImplUtils.getColor(ColorType.TEXT_DISABLED));
        }
    }

    /**
     * Pop the disabled text color if something is inactive.
     *
     * @param active Whether the thing is active.
     */
    private static void popInactiveColor(boolean active) {
        if (!active) {
            IkGui.popStyleColor();
        }
    }

    /**
     * Fetch one of the debug rectangles of a window.
     *
     * @param window The window.
     * @param rectType Which rectangle, from {@link #WINDOW_RECT_NAMES}.
     * @return A new rectangle.
     */
    private static RectFloat getWindowRect(@NonNull Window window, int rectType) {
        return switch (rectType) {
            case WRT_OUTER_RECT_CLIPPED -> new RectFloat(window.rectOuterClipped);
            case WRT_INNER_RECT -> new RectFloat(window.rectInner);
            case WRT_INNER_CLIP_RECT -> new RectFloat(window.rectInnerClip);
            case WRT_WORK_RECT -> new RectFloat(window.rectWork);
            case WRT_CONTENT, WRT_CONTENT_IDEAL -> {
                final float minX =
                        window.rectInner.getLeft() - window.scrollPosition.x + window.padding.x;
                final float minY =
                        window.rectInner.getTop() - window.scrollPosition.y + window.padding.y;
                final Vector2f size =
                        rectType == WRT_CONTENT ? window.contentSize : window.contentSizeIdeal;
                yield new RectFloat(minX, minY, minX + size.x, minY + size.y);
            }
            case WRT_CONTENT_REGION_RECT -> new RectFloat(window.rectContent);
            default -> window.getRect();
        };
    }

    /**
     * Fetch one of the debug rectangles of a table.
     *
     * @param table The table.
     * @param rectType Which rectangle, from {@link #TABLE_RECT_NAMES}.
     * @param n The column index, for the column rectangles.
     * @return A new rectangle.
     */
    private static RectFloat getTableRect(@NonNull Table table, int rectType, int n) {
        if (rectType >= TRT_COLUMNS_RECT) {
            if (n < 0 || n >= table.columnsCount) {
                return new RectFloat(0, 0, 0, 0);
            }
            return getTableColumnRect(table, table.columns[n], rectType);
        }
        return switch (rectType) {
            case TRT_INNER_RECT -> new RectFloat(table.innerRect);
            case TRT_WORK_RECT -> new RectFloat(table.workRect);
            case TRT_HOST_CLIP_RECT -> new RectFloat(table.hostClipRect);
            case TRT_INNER_CLIP_RECT -> new RectFloat(table.innerClipRect);
            case TRT_BACKGROUND_CLIP_RECT -> new RectFloat(table.backgroundClipRect);
            default -> new RectFloat(table.outerRect);
        };
    }

    /**
     * Fetch one of the debug rectangles of a table column.
     *
     * @param table The table.
     * @param c The column.
     * @param rectType Which rectangle, one of the column types from {@link #TABLE_RECT_NAMES}.
     * @return A new rectangle.
     */
    private static RectFloat getTableColumnRect(
            @NonNull Table table, @NonNull TableColumn c, int rectType) {
        // Always using the last submitted instance
        final TableInstanceData instance = table.getInstanceData(table.instanceCurrent);
        final float innerClipTop = table.innerClipRect.getTop();
        return switch (rectType) {
            case TRT_COLUMNS_RECT ->
                    new RectFloat(
                            c.minX, innerClipTop, c.maxX, innerClipTop + instance.lastOuterHeight);
            case TRT_COLUMNS_WORK_RECT ->
                    new RectFloat(
                            c.workMinX,
                            table.workRect.getTop(),
                            c.workMaxX,
                            table.workRect.getBottom());
            case TRT_COLUMNS_CLIP_RECT -> new RectFloat(c.clipRect);
            case TRT_COLUMNS_CONTENT_HEADERS_USED ->
                    new RectFloat(
                            c.workMinX,
                            innerClipTop,
                            c.contentMaxXHeadersUsed,
                            innerClipTop + instance.lastTopHeadersRowHeight);
            case TRT_COLUMNS_CONTENT_HEADERS_IDEAL ->
                    new RectFloat(
                            c.workMinX,
                            innerClipTop,
                            c.contentMaxXHeadersIdeal,
                            innerClipTop + instance.lastTopHeadersRowHeight);
            case TRT_COLUMNS_CONTENT_FROZEN ->
                    new RectFloat(
                            c.workMinX,
                            innerClipTop,
                            c.contentMaxXFrozen,
                            innerClipTop + instance.lastFrozenHeight);
            case TRT_COLUMNS_CONTENT_UNFROZEN ->
                    new RectFloat(
                            c.workMinX,
                            innerClipTop + instance.lastFrozenHeight,
                            c.contentMaxXUnfrozen,
                            table.innerClipRect.getBottom());
            default -> new RectFloat(0, 0, 0, 0);
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Flashing style colors
    // ---------------------------------------------------------------------------------------------

    /**
     * Flash a style color for a short time, to help find where it is used. While flashing, pushing
     * the style color has no effect.
     *
     * @param type The style color to flash.
     */
    static void debugFlashStyleColor(@NonNull ColorType type) {
        context.debugFlashStyleColorTime = 500;
        context.debugFlashStyleColor = type;
        updateDebugToolFlashStyleColorValue();
    }

    /** Update the flashing color, called by newFrame(). */
    static void updateDebugToolFlashStyleColor() {
        if (context.debugFlashStyleColorTime <= 0 || context.debugFlashStyleColor == null) {
            return;
        }
        updateDebugToolFlashStyleColorValue();
        context.debugFlashStyleColorTime -= context.io.deltaTime;
        if (context.debugFlashStyleColorTime <= 0) {
            context.debugFlashStyleColor = null;
        }
    }

    /** Calculate the current value of the flashing color. */
    private static void updateDebugToolFlashStyleColorValue() {
        final float seconds = context.debugFlashStyleColorTime / 1000.0f;
        final float[] hsv = {(float) Math.cos(seconds * 6.0f) * 0.5f + 0.5f, 0.5f, 0.5f};
        final float[] rgb = new float[3];
        IkGuiImplUtils.colorConvertHSVtoRGB(hsv, rgb);
        context.debugFlashStyleColorValue = Color.rgba(rgb[0], rgb[1], rgb[2], 1.0f);
    }

    // ---------------------------------------------------------------------------------------------
    // Visual helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Draw a thumbnail of a viewport and the windows in it.
     *
     * @param drawList The draw list to draw the outline into.
     * @param viewport The viewport.
     * @param bb Where to draw the thumbnail.
     */
    static void debugRenderViewportThumbnail(
            @NonNull DrawList drawList, @NonNull Viewport viewport, @NonNull RectFloat bb) {
        final Window window = context.windowCurrent;

        final float scaleX = bb.getWidth() / Math.max(1.0f, viewport.size.x);
        final float scaleY = bb.getHeight() / Math.max(1.0f, viewport.size.y);
        final float offsetX = bb.getLeft() - viewport.position.x * scaleX;
        final float offsetY = bb.getTop() - viewport.position.y * scaleY;
        final float alphaMultiplier =
                (viewport.flags & ViewportFlags.IS_MINIMIZED) != 0 ? 0.30f : 1.00f;
        window.drawList.addRectFilled(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                IkGuiImplUtils.getColor(ColorType.BORDER, alphaMultiplier * 0.40f));
        final RectFloat titleBar = new RectFloat();
        for (Window thumbWindow : context.windowDisplayOrder) {
            if (!thumbWindow.wasActive
                    || (thumbWindow.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0
                    || thumbWindow.viewport != viewport) {
                continue;
            }

            final RectFloat thumbRect = thumbWindow.getRect();
            thumbWindow.getTitleBarRect(titleBar);
            final RectFloat thumb =
                    new RectFloat(
                            IkGuiInternal.truncate(offsetX + thumbRect.getLeft() * scaleX),
                            IkGuiInternal.truncate(offsetY + thumbRect.getTop() * scaleY),
                            IkGuiInternal.truncate(offsetX + thumbRect.getRight() * scaleX),
                            IkGuiInternal.truncate(offsetY + thumbRect.getBottom() * scaleY));
            // Exaggerate the title bar height
            final RectFloat title =
                    new RectFloat(
                            IkGuiInternal.truncate(offsetX + titleBar.getLeft() * scaleX),
                            IkGuiInternal.truncate(offsetY + titleBar.getTop() * scaleY),
                            IkGuiInternal.truncate(offsetX + titleBar.getRight() * scaleX),
                            IkGuiInternal.truncate(
                                    offsetY
                                            + (titleBar.getTop() + titleBar.getHeight() * 3.0f)
                                                    * scaleY));
            thumb.clipWithFull(bb);
            title.clipWithFull(bb);
            final boolean windowIsFocused =
                    context.navFocusedWindow != null
                            && thumbWindow.rootWindowForTitleBarHighlight
                                    == context.navFocusedWindow.rootWindowForTitleBarHighlight;
            window.drawList.addRectFilled(
                    thumb.getLeft(),
                    thumb.getTop(),
                    thumb.getRight(),
                    thumb.getBottom(),
                    IkGuiImplUtils.getColor(ColorType.WINDOW_BACKGROUND, alphaMultiplier));
            window.drawList.addRectFilled(
                    title.getLeft(),
                    title.getTop(),
                    title.getRight(),
                    title.getBottom(),
                    IkGuiImplUtils.getColor(
                            windowIsFocused
                                    ? ColorType.TITLE_BACKGROUND_ACTIVE
                                    : ColorType.TITLE_BACKGROUND,
                            alphaMultiplier));
            window.drawList.addRect(
                    thumb.getLeft(),
                    thumb.getTop(),
                    thumb.getRight(),
                    thumb.getBottom(),
                    IkGuiImplUtils.getColor(ColorType.BORDER, alphaMultiplier));
            window.drawList.pushClipRect(
                    thumb.getLeft(), thumb.getTop(), thumb.getRight(), thumb.getBottom(), true);
            window.drawList.addText(
                    context.fontSize,
                    title.getLeft(),
                    title.getTop(),
                    IkGuiImplUtils.getColor(ColorType.TEXT, alphaMultiplier),
                    Hash.getDisplayedText(thumbWindow.name),
                    0.0f);
            window.drawList.popClipRect();
        }
        drawList.addRect(
                bb.getLeft(),
                bb.getTop(),
                bb.getRight(),
                bb.getBottom(),
                IkGuiImplUtils.getColor(ColorType.BORDER, alphaMultiplier));
        if (viewport.id == context.debugMetricsConfig.highlightViewportID) {
            window.drawList.addRect(
                    bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), HIGHLIGHT_COLOR);
        }
    }

    /** Draw the monitors and viewports, scaled down, as a minimap. */
    private static void renderViewportsThumbnails() {
        final Window window = context.windowCurrent;

        // Draw the monitors and calculate their boundaries
        final float scale = 1.0f / 8.0f;
        final RectFloat bbFull =
                new RectFloat(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        final RectFloat monitorRect = new RectFloat();
        final List<PlatformMonitor> monitors = context.platformIO.monitors;
        for (PlatformMonitor monitor : monitors) {
            bbFull.add(monitor.getMainRect(monitorRect));
        }
        for (Viewport viewport : context.viewports) {
            bbFull.add(viewport.getMainRect(monitorRect));
        }
        if (bbFull.isInverted()) {
            return;
        }
        final float offsetX = window.cursorPosition.x - bbFull.getLeft() * scale;
        final float offsetY = window.cursorPosition.y - bbFull.getTop() * scale;
        for (int n = 0; n < monitors.size(); ++n) {
            final PlatformMonitor monitor = monitors.get(n);
            final float minX = offsetX + monitor.mainPosition.x * scale;
            final float minY = offsetY + monitor.mainPosition.y * scale;
            final float maxX = offsetX + (monitor.mainPosition.x + monitor.mainSize.x) * scale;
            final float maxY = offsetY + (monitor.mainPosition.y + monitor.mainSize.y) * scale;
            window.drawList.addRect(
                    minX,
                    minY,
                    maxX,
                    maxY,
                    context.debugMetricsConfig.highlightMonitorIndex == n
                            ? HIGHLIGHT_COLOR
                            : IkGuiImplUtils.getColor(ColorType.BORDER),
                    4.0f);
            window.drawList.addRectFilled(
                    minX, minY, maxX, maxY, IkGuiImplUtils.getColor(ColorType.BORDER, 0.10f), 4.0f);
        }

        // Draw the viewports
        for (Viewport viewport : context.viewports) {
            final RectFloat viewportBB =
                    new RectFloat(
                            offsetX + viewport.position.x * scale,
                            offsetY + viewport.position.y * scale,
                            offsetX + (viewport.position.x + viewport.size.x) * scale,
                            offsetY + (viewport.position.y + viewport.size.y) * scale);
            debugRenderViewportThumbnail(window.drawList, viewport, viewportBB);
        }
        IkGui.dummy(bbFull.getWidth() * scale, bbFull.getHeight() * scale);
    }

    /**
     * Draw part of a US keyboard layout, to visualize which keys are held.
     *
     * @param drawList The draw list to draw into.
     */
    static void debugRenderKeyboardPreview(@NonNull DrawList drawList) {
        final float scale = IkGuiInternal.getFontSize() / 13.0f;
        final float keySize = 35.0f * scale;
        final float keyRounding = 3.0f * scale;
        final float keyFaceSize = 25.0f * scale;
        final float keyFacePosX = 5.0f * scale;
        final float keyFacePosY = 3.0f * scale;
        final float keyFaceRounding = 2.0f * scale;
        final float keyLabelPosX = 7.0f * scale;
        final float keyLabelPosY = 4.0f * scale;
        final float keyStep = keySize - 1.0f;
        final float keyRowOffset = 9.0f * scale;

        final Vector2f boardMin = IkGui.getCursorScreenPos();
        final float boardMaxX = boardMin.x + 3 * keyStep + 2 * keyRowOffset + 10.0f;
        final float boardMaxY = boardMin.y + 3 * keyStep + 10.0f;
        final float startX = boardMin.x + 5.0f - keyStep;
        final float startY = boardMin.y;

        record KeyLayoutData(int row, int column, String label, Key key) {}
        final KeyLayoutData[] keysToDisplay = {
            new KeyLayoutData(0, 0, "", Key.TAB),
            new KeyLayoutData(0, 1, "Q", Key.Q),
            new KeyLayoutData(0, 2, "W", Key.W),
            new KeyLayoutData(0, 3, "E", Key.E),
            new KeyLayoutData(0, 4, "R", Key.R),
            new KeyLayoutData(1, 0, "", Key.NONE),
            new KeyLayoutData(1, 1, "A", Key.A),
            new KeyLayoutData(1, 2, "S", Key.S),
            new KeyLayoutData(1, 3, "D", Key.D),
            new KeyLayoutData(1, 4, "F", Key.F),
            new KeyLayoutData(2, 0, "", Key.LEFT_SHIFT),
            new KeyLayoutData(2, 1, "Z", Key.Z),
            new KeyLayoutData(2, 2, "X", Key.X),
            new KeyLayoutData(2, 3, "C", Key.C),
            new KeyLayoutData(2, 4, "V", Key.V)
        };

        // Shapes drawn manually are not clipped automatically, so skip them when out of view
        IkGui.dummy(boardMaxX - boardMin.x, boardMaxY - boardMin.y);
        if (!IkGui.isItemVisible()) {
            return;
        }
        drawList.pushClipRect(boardMin.x, boardMin.y, boardMaxX, boardMaxY, true);
        for (KeyLayoutData keyData : keysToDisplay) {
            final float keyMinX = startX + keyData.column * keyStep + keyData.row * keyRowOffset;
            final float keyMinY = startY + keyData.row * keyStep;
            final float keyMaxX = keyMinX + keySize;
            final float keyMaxY = keyMinY + keySize;
            drawList.addRectFilled(
                    keyMinX,
                    keyMinY,
                    keyMaxX,
                    keyMaxY,
                    Color.rgba(204, 204, 204, 255),
                    keyRounding);
            drawList.addRect(
                    keyMinX, keyMinY, keyMaxX, keyMaxY, Color.rgba(24, 24, 24, 255), keyRounding);
            final float faceMinX = keyMinX + keyFacePosX;
            final float faceMinY = keyMinY + keyFacePosY;
            final float faceMaxX = faceMinX + keyFaceSize;
            final float faceMaxY = faceMinY + keyFaceSize;
            drawList.addRect(
                    faceMinX,
                    faceMinY,
                    faceMaxX,
                    faceMaxY,
                    Color.rgba(193, 193, 193, 255),
                    keyFaceRounding,
                    DrawFlags.ROUND_CORNERS_ALL,
                    2.0f);
            drawList.addRectFilled(
                    faceMinX,
                    faceMinY,
                    faceMaxX,
                    faceMaxY,
                    Color.rgba(252, 252, 252, 255),
                    keyFaceRounding);
            drawList.addText(
                    context.fontSize,
                    keyMinX + keyLabelPosX,
                    keyMinY + keyLabelPosY,
                    Color.rgba(64, 64, 64, 255),
                    keyData.label,
                    0.0f);
            if (keyData.key != Key.NONE && IkGui.isKeyDown(keyData.key)) {
                drawList.addRectFilled(
                        keyMinX,
                        keyMinY,
                        keyMaxX,
                        keyMaxY,
                        Color.rgba(255, 0, 0, 128),
                        keyRounding);
            }
        }
        drawList.popClipRect();
    }

    /**
     * Display the code points and UTF-8 bytes of a string, to diagnose text encoding issues versus
     * font loading issues.
     *
     * @param text The text to inspect.
     */
    static void debugTextEncoding(@NonNull String text) {
        IkGui.text("Text: \"" + text + "\"");
        if (!IkGui.beginTable(
                "##DebugTextEncoding",
                4,
                TableFlags.BORDERS
                        | TableFlags.ROW_BACKGROUND
                        | TableFlags.SIZING_FIXED_FIT
                        | TableFlags.RESIZABLE)) {
            return;
        }
        IkGui.tableSetupColumn("Offset");
        IkGui.tableSetupColumn("UTF-8");
        IkGui.tableSetupColumn("Glyph");
        IkGui.tableSetupColumn("Codepoint");
        IkGui.tableHeadersRow();
        int byteOffset = 0;
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            final String character = new String(Character.toChars(codePoint));
            final byte[] utf8 = character.getBytes(StandardCharsets.UTF_8);
            IkGui.tableNextColumn();
            IkGui.text(Integer.toString(byteOffset));
            IkGui.tableNextColumn();
            for (int b = 0; b < utf8.length; ++b) {
                if (b > 0) {
                    IkGui.sameLine();
                }
                IkGui.text(String.format("0x%02X", utf8[b] & 0xFF));
            }
            IkGui.tableNextColumn();
            IkGui.textUnformatted(character);
            if (codePoint > Character.MAX_VALUE
                    || context.font == null
                    || !context.font.supports((char) codePoint)) {
                IkGui.sameLine();
                IkGui.textUnformatted("[missing]");
            }
            IkGui.tableNextColumn();
            IkGui.text(String.format("U+%04X", codePoint));
            byteOffset += utf8.length;
            i += Character.charCount(codePoint);
        }
        IkGui.endTable();
    }

    // ---------------------------------------------------------------------------------------------
    // The metrics window
    // ---------------------------------------------------------------------------------------------

    /**
     * Show the metrics/debugger window, which displays the internal state of the library.
     *
     * @param open The open state of the window, may be null.
     */
    static void showMetricsWindow(IkBoolean open) {
        final IkIO io = context.io;
        final MetricsConfig cfg = context.debugMetricsConfig;
        if (cfg.showDebugLog.get()) {
            IkGuiImplDebugTools.showDebugLogWindow(cfg.showDebugLog);
        }
        if (cfg.showIDStackTool.get()) {
            IkGuiImplDebugTools.showIDStackToolWindow(cfg.showIDStackTool);
        }

        if (!IkGui.begin("IkGui Metrics/Debugger", open)
                || IkGuiInternal.getCurrentWindow().beginCount > 1) {
            IkGui.end();
            return;
        }

        // Clear debug break hooks after exactly one cycle
        IkGuiImplDebugTools.debugBreakClearData();

        // Basic info
        IkGui.text("IkGui, a port of Dear ImGui (docking branch)");
        IkGui.text(
                String.format(
                        "Application average %.3f ms/frame (%.1f FPS)",
                        1000.0f / io.framerate, io.framerate));
        IkGui.text(
                String.format(
                        "%d vertices, %d visible windows, %d active windows",
                        io.metricsRenderVertices,
                        io.metricsRenderWindows,
                        io.metricsActiveWindows));

        IkGui.separator();

        if (cfg.showWindowsRectsType.get() < 0) {
            cfg.showWindowsRectsType.set(WRT_WORK_RECT);
        }
        if (cfg.showTablesRectsType.get() < 0) {
            cfg.showTablesRectsType.set(TRT_WORK_RECT);
        }

        showToolsSection();
        showWindowsSection();
        showDrawListsSection();
        showViewportsSection();
        showFontsSection();
        showPopupsSection();
        showTabBarsSection();
        showTablesSection();
        showInputTextSection();
        showTypingSelectSection();
        showMultiSelectSection();
        showDockingSection();
        showSettingsSection();
        showMemorySection();
        showInputsSection();
        showInternalStateSection();

        showOverlays();

        IkGui.end();
    }

    /** The tools section of the metrics window. */
    private static void showToolsSection() {
        final MetricsConfig cfg = context.debugMetricsConfig;
        if (!IkGui.treeNode("Tools")) {
            return;
        }

        // The item picker is useful to visually select an item and break into the call stack of
        // where it was submitted
        final Vector2f helpSize = IkGui.calcTextSize("(?)");
        IkGuiImplLayout.separatorTextEx(
                0, "Debug breaks", helpSize.x + context.style.variable.separatorTextPadding.x);
        IkGui.sameLine();
        metricsHelpMarker(
                "Will call IkGuiInternal.debugBreak(), put a breakpoint there to stop in the"
                        + " debugger and see the call stack.");
        if (IkGui.checkbox("Show Item Picker", context.debugItemPickerActive)
                && context.debugItemPickerActive.get()) {
            IkGuiImplDebugTools.debugStartItemPicker();
        }
        final IkBoolean debuggerPresent = new IkBoolean(context.io.configDebugIsDebuggerPresent);
        if (IkGui.checkbox(
                "Show \"Debug Break\" buttons in other sections"
                        + " (io.configDebugIsDebuggerPresent)",
                debuggerPresent)) {
            context.io.configDebugIsDebuggerPresent = debuggerPresent.get();
        }

        IkGui.separatorText("Visualize");

        IkGui.checkbox("Show Debug Log", cfg.showDebugLog);
        IkGui.sameLine();
        metricsHelpMarker("You can also call IkGui.showDebugLogWindow() from your code.");

        IkGui.checkbox("Show ID Stack Tool", cfg.showIDStackTool);
        IkGui.sameLine();
        metricsHelpMarker("You can also call IkGui.showIDStackToolWindow() from your code.");

        IkGui.checkbox("Show windows begin order", cfg.showWindowsBeginOrder);
        IkGui.checkbox("Show windows rectangles", cfg.showWindowsRects);
        IkGui.sameLine();
        IkGui.setNextItemWidth(IkGuiInternal.getFontSize() * 12);
        if (IkGui.combo("##show_windows_rect_type", cfg.showWindowsRectsType, WINDOW_RECT_NAMES)) {
            cfg.showWindowsRects.set(true);
        }
        if (cfg.showWindowsRects.get() && context.navFocusedWindow != null) {
            IkGui.bulletText("'" + context.navFocusedWindow.name + "':");
            IkGui.indent();
            for (int rectType = 0; rectType < WINDOW_RECT_NAMES.length; ++rectType) {
                final RectFloat rect = getWindowRect(context.navFocusedWindow, rectType);
                IkGui.text(formatRect(rect) + " " + WINDOW_RECT_NAMES[rectType]);
            }
            IkGui.unindent();
        }

        IkGui.checkbox("Show tables rectangles", cfg.showTablesRects);
        IkGui.sameLine();
        IkGui.setNextItemWidth(IkGuiInternal.getFontSize() * 12);
        if (IkGui.combo("##show_table_rects_type", cfg.showTablesRectsType, TABLE_RECT_NAMES)) {
            cfg.showTablesRects.set(true);
        }
        if (cfg.showTablesRects.get() && context.navFocusedWindow != null) {
            for (Table table : context.tables) {
                if (table == null
                        || table.lastFrameActive < context.frameCount - 1
                        || (table.outerWindow != context.navFocusedWindow
                                && table.innerWindow != context.navFocusedWindow)) {
                    continue;
                }

                IkGui.bulletText(
                        String.format(
                                "Table 0x%08X (%d columns, in '%s')",
                                table.id, table.columnsCount, table.outerWindow.name));
                if (IkGui.isItemHovered()) {
                    final RectFloat rect = new RectFloat(table.outerRect);
                    rect.expand(1.0f, 1.0f);
                    highlight(table.outerWindow, rect);
                }
                IkGui.indent();
                for (int rectType = 0; rectType < TABLE_RECT_NAMES.length; ++rectType) {
                    if (rectType >= TRT_COLUMNS_RECT) {
                        if (rectType != TRT_COLUMNS_RECT && rectType != TRT_COLUMNS_CLIP_RECT) {
                            continue;
                        }
                        for (int column = 0; column < table.columnsCount; ++column) {
                            final RectFloat rect = getTableRect(table, rectType, column);
                            IkGui.selectable(
                                    formatRect(rect)
                                            + " Col "
                                            + column
                                            + " "
                                            + TABLE_RECT_NAMES[rectType]);
                            if (IkGui.isItemHovered()) {
                                rect.expand(1.0f, 1.0f);
                                highlight(table.outerWindow, rect);
                            }
                        }
                    } else {
                        final RectFloat rect = getTableRect(table, rectType, -1);
                        IkGui.selectable(formatRect(rect) + " " + TABLE_RECT_NAMES[rectType]);
                        if (IkGui.isItemHovered()) {
                            rect.expand(1.0f, 1.0f);
                            highlight(table.outerWindow, rect);
                        }
                    }
                }
                IkGui.unindent();
            }
        }
        final IkBoolean showGroupRects = new IkBoolean(context.debugShowGroupRects);
        if (IkGui.checkbox("Show groups rectangles", showGroupRects)) {
            context.debugShowGroupRects = showGroupRects.get();
        }

        IkGui.separatorText("Validate");

        final IkBoolean beginReturnValueLoop =
                new IkBoolean(context.io.configDebugBeginReturnValueLoop);
        if (IkGui.checkbox("Debug Begin/BeginChild return value", beginReturnValueLoop)) {
            context.io.configDebugBeginReturnValueLoop = beginReturnValueLoop.get();
        }
        IkGui.sameLine();
        metricsHelpMarker(
                "Some calls to begin()/beginChild() will return false.\n\nWill cycle through"
                        + " window depths then repeat. Windows should be flickering while"
                        + " running.");

        IkGui.checkbox("UTF-8 Encoding viewer", cfg.showTextEncodingViewer);
        IkGui.sameLine();
        metricsHelpMarker(
                "You can also call IkGui.debugTextEncoding() from your code with a given string"
                        + " to test that your text encoding is correct.");
        if (cfg.showTextEncodingViewer.get()) {
            IkGui.setNextItemWidth(-Float.MIN_VALUE);
            IkGui.inputText("##DebugTextEncodingBuf", cfg.textEncodingBuffer);
            final String text = cfg.textEncodingBuffer.get();
            if (!text.isEmpty()) {
                debugTextEncoding(text);
            }
        }

        IkGui.treePop();
    }

    /** The windows section of the metrics window. */
    private static void showWindowsSection() {
        if (!IkGui.treeNode("Windows", "Windows (" + context.windowDisplayOrder.size() + ")")) {
            return;
        }
        debugNodeWindowsList(context.windowDisplayOrder, "By display order");
        debugNodeWindowsList(context.windowFocusOrder, "By focus order (root windows)");
        if (IkGui.treeNode("By submission order (begin stack)")) {
            // Here we display windows in their submitted order/hierarchy, however note that the
            // begin stack doesn't constitute a parent/child relationship
            final List<Window> windows = new ArrayList<>();
            for (Window window : context.windowDisplayOrder) {
                if (window.lastFrameActive + 1 >= context.frameCount) {
                    windows.add(window);
                }
            }
            windows.sort(Comparator.comparingInt(window -> window.beginOrderWithinContext));
            debugNodeWindowsListByBeginStackParent(windows, 0, null);
            IkGui.treePop();
        }
        IkGui.treePop();
    }

    /** The draw lists section of the metrics window. */
    private static void showDrawListsSection() {
        final MetricsConfig cfg = context.debugMetricsConfig;
        int drawListCount = 0;
        for (Viewport viewport : context.viewports) {
            drawListCount += viewport.drawData.drawLists.size();
        }
        if (!IkGui.treeNode("DrawLists", "DrawLists (" + drawListCount + ")")) {
            return;
        }
        IkGui.checkbox("Show draw command quads when hovering", cfg.showDrawCmdMesh);
        IkGui.checkbox(
                "Show draw command bounding boxes when hovering", cfg.showDrawCmdBoundingBoxes);
        for (Viewport viewport : context.viewports) {
            boolean viewportHasDrawList = false;
            for (DrawList drawList : new ArrayList<>(viewport.drawData.drawLists)) {
                if (!viewportHasDrawList) {
                    IkGui.text(
                            String.format(
                                    "Active DrawLists in Viewport #%d, ID: 0x%08X",
                                    viewport.index, viewport.id));
                }
                viewportHasDrawList = true;
                debugNodeDrawList(null, viewport, drawList, "DrawList");
            }
        }
        IkGui.treePop();
    }

    /** The viewports section of the metrics window. */
    private static void showViewportsSection() {
        final MetricsConfig cfg = context.debugMetricsConfig;
        if (!IkGui.treeNode("Viewports", "Viewports (" + context.viewports.size() + ")")) {
            return;
        }
        cfg.highlightMonitorIndex = -1;
        final List<PlatformMonitor> monitors = context.platformIO.monitors;
        final boolean open = IkGui.treeNode("Monitors", "Monitors (" + monitors.size() + ")");
        IkGui.sameLine();
        metricsHelpMarker(
                "We use monitor data:\n- to query DPI settings on a per monitor basis\n- to"
                        + " position popup/tooltips so they don't straddle monitors.");
        if (open) {
            for (int i = 0; i < monitors.size(); ++i) {
                debugNodePlatformMonitor(monitors.get(i), "Monitor", i);
                if (IkGui.isItemHovered()) {
                    cfg.highlightMonitorIndex = i;
                }
            }
            debugNodePlatformMonitor(context.fallbackMonitor, "Fallback", 0);
            IkGui.treePop();
        }

        IkGui.setNextItemOpen(true, Condition.ONCE);
        if (IkGui.treeNode("Windows Minimap")) {
            renderViewportsThumbnails();
            IkGui.treePop();
        }
        cfg.highlightViewportID = 0;

        IkGui.bulletText(
                String.format(
                        "MouseViewport: 0x%08X (UserHovered 0x%08X, LastHovered 0x%08X)",
                        context.mouseViewport != null ? context.mouseViewport.id : 0,
                        context.io.mouseHoveredViewport,
                        context.mouseLastHoveredViewport != null
                                ? context.mouseLastHoveredViewport.id
                                : 0));
        if (IkGui.treeNode("Inferred Z order (front-to-back)")) {
            final List<Viewport> viewports = new ArrayList<>(context.viewports);
            viewports.sort(
                    Comparator.comparingInt((Viewport viewport) -> viewport.lastFocusedStampCount)
                            .reversed());
            for (Viewport viewport : viewports) {
                final String platformFocused =
                        (context.platformIO.platformGetWindowFocus != null
                                        && viewport.platformWindowCreated)
                                ? (context.platformIO.platformGetWindowFocus.test(viewport)
                                        ? "1"
                                        : "0")
                                : "N/A";
                IkGui.bulletText(
                        String.format(
                                "Viewport #%d, ID: 0x%08X, LastFocused = %08d, PlatformFocused ="
                                        + " %s, Window: \"%s\"",
                                viewport.index,
                                viewport.id,
                                viewport.lastFocusedStampCount,
                                platformFocused,
                                viewport.window != null ? viewport.window.name : "N/A"));
                if (IkGui.isItemHovered()) {
                    cfg.highlightViewportID = viewport.id;
                }
            }
            IkGui.treePop();
        }

        for (Viewport viewport : new ArrayList<>(context.viewports)) {
            debugNodeViewport(viewport);
        }
        IkGui.treePop();
    }

    /** The fonts section of the metrics window, adapted to our font cache. */
    private static void showFontsSection() {
        final FontAtlas atlas = context.io.fonts;
        if (atlas == null) {
            return;
        }
        if (!IkGui.treeNode("Fonts", "Fonts (" + atlas.getFontNames().size() + ")")) {
            return;
        }
        showFontAtlas(atlas);
        IkGui.treePop();
    }

    /**
     * A combo to pick the current font from the loaded fonts.
     *
     * @param label The label of the combo.
     */
    static void showFontSelector(String label) {
        final FontAtlas atlas = context.io.fonts;
        final String current = context.font != null ? context.font.name : "";
        if (atlas != null && IkGui.beginCombo(label, current)) {
            for (String name : atlas.getFontNames()) {
                IkGui.pushID(name);
                if (IkGui.selectable(name, name.equals(current), SelectableFlags.SELECT_ON_NAV)) {
                    IkGui.setFont(name);
                }
                if (name.equals(current)) {
                    IkGui.setItemDefaultFocus();
                }
                IkGui.popID();
            }
            IkGui.endCombo();
        }
        IkGui.sameLine();
        metricsHelpMarker(
                "- Load additional fonts with io.fonts.loadFont().\n"
                        + "- Fonts are rasterized on demand, so any size can be used.");
    }

    /**
     * Show the loaded fonts and the font atlas, adapted to our font cache. Used by the metrics
     * window and the style editor.
     *
     * @param atlas The font atlas.
     */
    static void showFontAtlas(@NonNull FontAtlas atlas) {
        final List<String> fontNames = atlas.getFontNames();
        IkGui.text(
                String.format(
                        "Current font: '%s', size %d",
                        context.font != null ? context.font.name : "NULL", context.fontSize));
        final StringBuilder fallbacks = new StringBuilder();
        context.fontFallbacks.forEach(
                font -> fallbacks.append(fallbacks.isEmpty() ? "" : ", ").append(font.name));
        IkGui.text("Fallbacks: " + (fallbacks.isEmpty() ? "none" : fallbacks));
        IkGui.text(String.format("Fallback character: '%c'", context.fontFallbackChar));
        final FontMetrics current = atlas.getFontMetrics(context.fontSize);
        if (current != null) {
            IkGui.text(
                    String.format(
                            "Line height: %.0f, baseline at %.0f",
                            IkGuiInternal.getFontSize(),
                            Math.floor(current.baselineOffset(IkGuiInternal.getFontSize()))));
        }

        IkGui.separatorText("Font List");
        for (String name : fontNames) {
            final FontMetrics metrics = atlas.getFontMetrics(name, context.fontSize);
            if (metrics == null) {
                IkGui.bulletText(name);
                continue;
            }
            IkGui.bulletText(
                    String.format(
                            "%s: ascent %.2f, descent %.2f, line gap %.2f at size %d",
                            name,
                            metrics.ascent(),
                            metrics.descent(),
                            metrics.lineGap(),
                            context.fontSize));
        }

        IkGui.separatorText("Font Atlas");
        IkGui.text(
                String.format(
                        "Texture: %dx%d, %d cached characters, %d shelves using %d px",
                        FontAtlas.FONT_ATLAS_IMAGE_WIDTH,
                        FontAtlas.FONT_ATLAS_IMAGE_HEIGHT,
                        atlas.getCachedCharacterCount(),
                        atlas.getShelfCount(),
                        atlas.getShelvesHeight()));
        IkGui.text("Bitmaps waiting for upload: " + atlas.stagedBitmaps.size());

        if (IkGui.treeNode(
                "Cached characters",
                "Cached characters (most recently used first, up to "
                        + MAX_LISTED_CHARACTERS
                        + ")")) {
            final List<FontAtlas.CharInfo> characters =
                    atlas.getCachedCharacters(MAX_LISTED_CHARACTERS);
            final ListClipper clipper = new ListClipper();
            clipper.begin(characters.size());
            while (clipper.step()) {
                for (int n = clipper.displayStart; n < clipper.displayEnd; ++n) {
                    final FontAtlas.CharInfo info = characters.get(n);
                    IkGui.text(
                            String.format(
                                    "'%c' U+%04X at (%4d,%4d) size %2dx%2d bearing (%d,%d)",
                                    info.value,
                                    (int) info.value,
                                    info.x,
                                    info.y,
                                    info.width,
                                    info.height,
                                    info.bearingX,
                                    info.bearingY));
                }
            }
            IkGui.treePop();
        }

        if (atlas.texture != null) {
            IkGui.setNextItemOpen(true, Condition.ONCE);
            if (IkGui.treeNode("Texture")) {
                // Only show the part of the texture that is in use
                final float usedHeight =
                        Math.max(
                                1,
                                Math.min(
                                        atlas.getShelvesHeight(),
                                        FontAtlas.FONT_ATLAS_IMAGE_HEIGHT));
                final float width = Math.min(512.0f, IkGui.getContentRegionAvailableX());
                final float scale = width / FontAtlas.FONT_ATLAS_IMAGE_WIDTH;
                IkGui.image(
                        atlas.texture,
                        width,
                        usedHeight * scale,
                        0.0f,
                        0.0f,
                        1.0f,
                        usedHeight / FontAtlas.FONT_ATLAS_IMAGE_HEIGHT);
                IkGui.treePop();
            }
        } else {
            IkGui.textDisabled("The renderer did not provide the atlas texture.");
        }
    }

    /** The popups section of the metrics window. */
    private static void showPopupsSection() {
        if (!IkGui.treeNode("Popups", "Popups (" + context.openPopupStack.size() + ")")) {
            return;
        }
        for (PopupData popupData : context.openPopupStack) {
            // As it's difficult to interact with tree nodes while popups are open, we display
            // everything inline
            final Window window = popupData.window;
            IkGui.bulletText(
                    String.format(
                            "PopupID: %08x, Window: '%s' (%s%s), RestoreNavWindow '%s',"
                                    + " ParentWindow '%s'",
                            popupData.popupID,
                            window != null ? window.name : "NULL",
                            window != null
                                            && (window.flags & WindowFlags.INTERNAL_CHILD_WINDOW)
                                                    != 0
                                    ? "Child;"
                                    : "",
                            window != null && (window.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0
                                    ? "Menu;"
                                    : "",
                            popupData.restoreNavWindow != null
                                    ? popupData.restoreNavWindow.name
                                    : "NULL",
                            window != null && window.parentWindow != null
                                    ? window.parentWindow.name
                                    : "NULL"));
        }
        IkGui.treePop();
    }

    /** The tab bars section of the metrics window. */
    private static void showTabBarsSection() {
        if (!IkGui.treeNode("TabBars", "Tab Bars (" + context.tabBars.size() + ")")) {
            return;
        }
        for (TabBar tabBar : new ArrayList<>(context.tabBars.values())) {
            IkGui.pushID(tabBar.id);
            debugNodeTabBar(tabBar, "TabBar");
            IkGui.popID();
        }
        IkGui.treePop();
    }

    /** The tables section of the metrics window. */
    private static void showTablesSection() {
        int aliveCount = 0;
        for (Table table : context.tables) {
            if (table != null) {
                ++aliveCount;
            }
        }
        if (!IkGui.treeNode("Tables", "Tables (" + aliveCount + ")")) {
            return;
        }
        for (Table table : new ArrayList<>(context.tables)) {
            if (table != null) {
                debugNodeTable(table);
            }
        }
        IkGui.treePop();
    }

    /** The input text section of the metrics window. */
    private static void showInputTextSection() {
        if (!IkGui.treeNode("InputText")) {
            return;
        }
        debugNodeInputTextState(context.inputTextState);
        IkGui.treePop();
    }

    /** The typing-select section of the metrics window. */
    private static void showTypingSelectSection() {
        final TypingSelectState state = context.typingSelectState;
        if (!IkGui.treeNode(
                "TypingSelect", "TypingSelect (" + (state.searchBuffer.isEmpty() ? 0 : 1) + ")")) {
            return;
        }
        debugNodeTypingSelectState(state);
        IkGui.treePop();
    }

    /**
     * Display the typing-select state.
     *
     * @param state The state.
     */
    static void debugNodeTypingSelectState(@NonNull TypingSelectState state) {
        IkGui.text("SearchBuffer = \"" + state.searchBuffer + "\"");
        IkGui.text(
                String.format(
                        "SingleCharMode = %d, Size = %d, Lock = %d",
                        state.request.singleCharMode ? 1 : 0,
                        state.request.singleCharSize,
                        state.singleCharModeLock ? 1 : 0));
        IkGui.text(
                String.format(
                        "LastRequest = time: %.2f, frame: %d",
                        state.lastRequestTime / 1000.0f, state.lastRequestFrame));
    }

    /** The multi-select section of the metrics window. */
    private static void showMultiSelectSection() {
        if (!IkGui.treeNode(
                "MultiSelect", "MultiSelect (" + context.multiSelectStorage.size() + ")")) {
            return;
        }
        final BoxSelectState bs = context.boxSelectState;
        IkGui.bulletText(
                String.format(
                        "BoxSelect ID=0x%08X, Starting = %d, Active %d",
                        bs.id, bs.isStarting ? 1 : 0, bs.isActive ? 1 : 0));
        for (MultiSelectState state : new ArrayList<>(context.multiSelectStorage.values())) {
            debugNodeMultiSelectState(state);
        }
        IkGui.treePop();
    }

    /**
     * Display a multi-select state.
     *
     * @param storage The state.
     */
    static void debugNodeMultiSelectState(@NonNull MultiSelectState storage) {
        // Note that fully clipped early out scrolling tables will appear as inactive here
        final boolean isActive = storage.lastFrameActive >= context.frameCount - 2;
        if (!isActive) {
            IkGui.pushStyleColor(ColorType.TEXT, IkGuiImplUtils.getColor(ColorType.TEXT_DISABLED));
        }
        final boolean open =
                IkGui.treeNode(
                        "MultiSelect" + storage.id,
                        String.format(
                                "MultiSelect 0x%08X in '%s'%s",
                                storage.id,
                                storage.window != null ? storage.window.name : "N/A",
                                isActive ? "" : " *Inactive*"));
        if (!isActive) {
            IkGui.popStyleColor();
        }
        if (!open) {
            return;
        }
        IkGui.text(
                String.format(
                        "RangeSrcItem = %d (0x%X), RangeSelected = %d",
                        storage.rangeSourceItem, storage.rangeSourceItem, storage.rangeSelected));
        IkGui.text(
                String.format(
                        "NavIdItem = %d (0x%X), NavIdSelected = %d",
                        storage.navIDItem, storage.navIDItem, storage.navIDSelected));
        // Provided by the user
        IkGui.text("LastSelectionSize = " + storage.lastSelectionSize);
        IkGui.treePop();
    }

    /** The docking section of the metrics window. */
    private static void showDockingSection() {
        final MetricsConfig cfg = context.debugMetricsConfig;
        if (!IkGui.treeNode("Docking")) {
            return;
        }
        final DockContext dockContext = context.dockContext;
        IkGui.checkbox("List root nodes", cfg.dockingRootNodesOnly);
        IkGui.checkbox("Ctrl shows window dock info", cfg.showDockingNodes);
        if (IkGui.smallButton("Clear nodes")) {
            IkGuiImplDocking.dockContextClearNodes(0, true);
        }
        IkGui.sameLine();
        if (IkGui.smallButton("Rebuild all")) {
            dockContext.wantFullRebuild = true;
        }
        for (DockNode node : new ArrayList<>(dockContext.nodes.values())) {
            if (!cfg.dockingRootNodesOnly.get() || node.isRootNode()) {
                debugNodeDockNode(node, "Node");
            }
        }
        IkGui.treePop();
    }

    /**
     * Push the highlight color if a settings entry is older than the cutoff date.
     *
     * @param cutoffDate The cutoff date as YYYYMMDD, or 0 for no highlighting.
     * @param date The date of the entry as YYYYMMDD.
     * @return True if the color was pushed and needs to be popped.
     */
    private static boolean pushHighlightOlderThan(int cutoffDate, int date) {
        final boolean highlight = cutoffDate != 0 && date < cutoffDate;
        if (highlight) {
            IkGui.pushStyleColor(ColorType.TEXT, Color.rgba(255, 102, 102, 255));
        }
        return highlight;
    }

    /** The settings section of the metrics window. */
    private static void showSettingsSection() {
        final MetricsConfig cfg = context.debugMetricsConfig;
        if (!IkGui.treeNode("Settings")) {
            return;
        }
        if (IkGui.smallButton("Clear")) {
            IkGuiImplConfig.clearIniSettings();
        }
        IkGui.sameLine();
        if (IkGui.smallButton("Save to memory")) {
            IkGuiImplConfig.saveIniSettingsToMemory();
        }
        IkGui.sameLine();
        IkGui.beginDisabled(context.io.iniFilename == null);
        if (IkGui.smallButton("Save to disk")) {
            IkGuiImplConfig.saveIniSettingsToDisk(context.io.iniFilename);
        }
        IkGui.endDisabled();
        IkGui.sameLine();
        IkGui.textUnformatted(
                context.io.iniFilename != null ? "\"" + context.io.iniFilename + "\"" : "<NULL>");
        IkGui.text(String.format("SettingsDirtyTimer %.2f", context.settingsDirtyTimer / 1000.0f));

        int highlightOlderThanDate = 0;
        final int sessionDate = context.platformIO.platformSessionDate;
        IkGui.text("SessionDate: " + sessionDate);
        IkGui.beginDisabled(sessionDate == 0);
        IkGui.checkbox("Highlight Entries Older Than", cfg.settingsHighlightOldEntries);
        IkGui.setNextItemWidth(IkGuiInternal.getFontSize() * 8);
        IkGui.sameLine();
        final int[] months = {cfg.settingsDiscardMonths.get()};
        if (IkGui.sliderInt("Months", months, 1, 24)) {
            cfg.settingsDiscardMonths.set(months[0]);
        }
        if (cfg.settingsHighlightOldEntries.get() && cfg.settingsDiscardMonths.get() > 0) {
            highlightOlderThanDate =
                    IkGuiImplConfig.subtractMonths(sessionDate, cfg.settingsDiscardMonths.get());
            IkGui.sameLine();
            if (IkGui.button("Discard")) {
                final SettingsCleanupArgs cleanupArgs = new SettingsCleanupArgs();
                cleanupArgs.discardOlderThanMonths = cfg.settingsDiscardMonths.get();
                IkGuiImplConfig.cleanupIniSettings(cleanupArgs);
            }
        }
        IkGui.endDisabled();
        final IkBoolean debugIniSettings = new IkBoolean(context.io.configDebugIniSettings);
        if (IkGui.checkbox("io.configDebugIniSettings", debugIniSettings)) {
            context.io.configDebugIniSettings = debugIniSettings.get();
        }

        if (IkGui.treeNode(
                "SettingsHandlers",
                "Settings handlers: (" + context.settingsHandlers.size() + ")")) {
            for (SettingsHandler handler : context.settingsHandlers) {
                IkGui.bulletText("\"" + handler.typeName + "\"");
            }
            IkGui.treePop();
        }
        if (IkGui.treeNode(
                "SettingsWindows", "Settings data: Windows: " + context.settingsWindows.size())) {
            for (WindowSettings settings : new ArrayList<>(context.settingsWindows)) {
                final boolean highlighted =
                        pushHighlightOlderThan(highlightOlderThanDate, settings.lastUsedDate);
                debugNodeWindowSettings(settings);
                if (highlighted) {
                    IkGui.popStyleColor();
                }
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode(
                "SettingsTables", "Settings data: Tables: " + context.settingsTables.size())) {
            for (TableSettings settings : new ArrayList<>(context.settingsTables)) {
                final boolean highlighted =
                        pushHighlightOlderThan(highlightOlderThanDate, settings.lastUsedDate);
                debugNodeTableSettings(settings, null);
                if (highlighted) {
                    IkGui.popStyleColor();
                }
            }
            IkGui.treePop();
        }

        if (IkGui.treeNode("SettingsDocking", "Settings data: Docking")) {
            IkGui.text("In SettingsWindows:");
            for (WindowSettings settings : context.settingsWindows) {
                if (settings.dockID != 0) {
                    IkGui.bulletText(
                            String.format(
                                    "Window '%s' -> DockId %08X DockOrder=%d",
                                    settings.name, settings.dockID, settings.dockOrder));
                }
            }
            IkGui.text("In SettingsNodes:");
            for (DockNodeSettings settings : context.dockContext.nodeSettings) {
                String selectedTabName = null;
                if (settings.selectedTabID != 0) {
                    final Window window = IkGuiInternal.findWindowByID(settings.selectedTabID);
                    if (window != null) {
                        selectedTabName = window.name;
                    } else {
                        final WindowSettings windowSettings =
                                IkGuiImplConfig.findWindowSettingsByID(settings.selectedTabID);
                        if (windowSettings != null) {
                            selectedTabName = windowSettings.name;
                        }
                    }
                }
                IkGui.bulletText(
                        String.format(
                                "Node %08X, Parent %08X, SelectedTab %08X ('%s')",
                                settings.id,
                                settings.parentNodeID,
                                settings.selectedTabID,
                                selectedTabName != null
                                        ? selectedTabName
                                        : settings.selectedTabID != 0 ? "N/A" : ""));
            }
            IkGui.treePop();
        }

        final String iniData = context.settingsIniData.toString();
        if (IkGui.treeNode(
                "SettingsIniData",
                "Settings unpacked data (.ini): " + iniData.length() + " chars")) {
            final IkString iniText = new IkString(iniData, iniData.length() + 1);
            IkGui.inputTextMultiline(
                    "##Ini",
                    iniText,
                    -Float.MIN_VALUE,
                    IkGui.getTextLineHeight() * 20,
                    InputTextFlags.READ_ONLY,
                    null);
            IkGui.treePop();
        }
        IkGui.treePop();
    }

    /** The memory section of the metrics window, which shows the Java heap. */
    private static void showMemorySection() {
        if (!IkGui.treeNode("Memory")) {
            return;
        }
        final Runtime runtime = Runtime.getRuntime();
        final long used = runtime.totalMemory() - runtime.freeMemory();
        IkGui.text(
                String.format(
                        "Java heap: %.1f MB used, %.1f MB allocated, %.1f MB max",
                        used / 1_048_576.0,
                        runtime.totalMemory() / 1_048_576.0,
                        runtime.maxMemory() / 1_048_576.0));
        IkGui.text(
                String.format(
                        "Releasing selected unused buffers after: %.2f secs",
                        context.io.configMemoryCompactTimer));
        IkGui.treePop();
    }

    /** The inputs section of the metrics window. */
    private static void showInputsSection() {
        final IkIO io = context.io;
        if (!IkGui.treeNode("Inputs")) {
            return;
        }
        IkGui.text("KEYBOARD/GAMEPAD/MOUSE KEYS");
        IkGui.indent();
        final StringBuilder down = new StringBuilder("Keys down:");
        final StringBuilder pressed = new StringBuilder("Keys pressed:");
        final StringBuilder released = new StringBuilder("Keys released:");
        for (Key key : Key.values()) {
            if (key == Key.NONE) {
                continue;
            }
            if (IkGui.isKeyDown(key)) {
                down.append(
                        String.format(
                                " \"%s\" (%.02f)",
                                IkGui.getKeyName(key),
                                io.keysDownDuration[key.ordinal()] / 1000.0f));
            }
            if (IkGui.isKeyPressed(key, false)) {
                pressed.append(" \"").append(IkGui.getKeyName(key)).append('"');
            }
            if (IkGui.isKeyReleased(key)) {
                released.append(" \"").append(IkGui.getKeyName(key)).append('"');
            }
        }
        IkGui.text(down.toString());
        IkGui.text(pressed.toString());
        IkGui.text(released.toString());
        IkGui.text(
                "Keys mods: "
                        + (io.keyCtrl ? "Ctrl " : "")
                        + (io.keyShift ? "Shift " : "")
                        + (io.keyAlt ? "Alt " : "")
                        + (io.keySuper ? "Super " : ""));
        final StringBuilder chars = new StringBuilder("Chars queue:");
        io.inputQueueCharacters
                .codePoints()
                .forEach(
                        c ->
                                chars.append(
                                        String.format(
                                                " '%s' (0x%04X)",
                                                c > ' ' ? new String(Character.toChars(c)) : "?",
                                                c)));
        IkGui.text(chars.toString());
        debugRenderKeyboardPreview(IkGui.getWindowDrawList());
        IkGui.unindent();

        IkGui.text("MOUSE STATE");
        IkGui.indent();
        if (IkGui.isMousePosValid()) {
            IkGui.text(
                    String.format("Mouse pos: (%g, %g)", io.mousePosition.x, io.mousePosition.y));
        } else {
            IkGui.text("Mouse pos: <INVALID>");
        }
        IkGui.text(String.format("Mouse delta: (%g, %g)", io.mouseDelta.x, io.mouseDelta.y));
        final StringBuilder mouseDown = new StringBuilder("Mouse down:");
        final StringBuilder mouseClicked = new StringBuilder("Mouse clicked:");
        final StringBuilder mouseReleased = new StringBuilder("Mouse released:");
        for (MouseButton button : MouseButton.values()) {
            if (button == MouseButton.NONE) {
                continue;
            }
            if (IkGui.isMouseDown(button)) {
                mouseDown.append(
                        String.format(
                                " b%d (%.02f secs)",
                                button.index, io.mouseDownDuration[button.index] / 1000.0f));
            }
            if (IkGui.isMouseClicked(button)) {
                mouseClicked.append(
                        String.format(
                                " b%d (%d)", button.index, io.mouseClickedCount[button.index]));
            }
            if (IkGui.isMouseReleased(button)) {
                mouseReleased.append(" b").append(button.index);
            }
        }
        IkGui.text(mouseDown.toString());
        IkGui.text(mouseClicked.toString());
        IkGui.text(mouseReleased.toString());
        IkGui.text(String.format("Mouse wheel: %.1f", io.mouseWheel));
        IkGui.text(
                String.format(
                        "MouseStationaryTimer: %.2f", context.io.mouseStationaryTimer / 1000.0f));
        IkGui.text("Mouse source: " + io.mouseSource);
        IkGui.unindent();

        IkGui.text("MOUSE WHEELING");
        IkGui.indent();
        IkGui.text(
                "WheelingWindow: '"
                        + (context.windowWheeling != null ? context.windowWheeling.name : "NULL")
                        + "'");
        IkGui.text(
                String.format(
                        "WheelingWindowReleaseTimer: %.2f",
                        context.windowWheelingReleaseTimer / 1000.0f));
        final Vector2f axisAverage = context.windowWheelingAxisAverage;
        IkGui.text(
                String.format(
                        "WheelingAxisAvg[] = { %.3f, %.3f }, Main Axis: %s",
                        axisAverage.x,
                        axisAverage.y,
                        axisAverage.x > axisAverage.y
                                ? "X"
                                : axisAverage.x < axisAverage.y ? "Y" : "<none>"));
        IkGui.unindent();

        IkGui.text("KEY OWNERS");
        IkGui.indent();
        if (IkGui.beginChild(
                "##owners",
                -Float.MIN_VALUE,
                IkGui.getTextLineHeightWithSpacing() * 8,
                ChildFlags.FRAME_STYLE | ChildFlags.RESIZE_Y,
                WindowFlags.NO_SAVED_SETTINGS)) {
            for (Key key : Key.values()) {
                final KeyOwnerData ownerData = context.keysOwnerData[key.ordinal()];
                if (ownerData == null || ownerData.ownerCurr == KeyRoutingData.KEY_OWNER_NO_OWNER) {
                    continue;
                }
                IkGui.text(
                        String.format(
                                "%s: 0x%08X%s",
                                IkGui.getKeyName(key),
                                ownerData.ownerCurr,
                                ownerData.lockUntilRelease
                                        ? " LockUntilRelease"
                                        : ownerData.lockThisFrame ? " LockThisFrame" : ""));
                IkGuiImplDebugTools.debugLocateItemOnHover(ownerData.ownerCurr);
            }
        }
        IkGui.endChild();
        IkGui.unindent();

        IkGui.text("SHORTCUT ROUTING");
        IkGui.sameLine();
        metricsHelpMarker("Declared shortcut routes automatically set key owner when mods match.");
        IkGui.indent();
        if (IkGui.beginChild(
                "##routes",
                -Float.MIN_VALUE,
                IkGui.getTextLineHeightWithSpacing() * 8,
                ChildFlags.FRAME_STYLE | ChildFlags.RESIZE_Y,
                WindowFlags.NO_SAVED_SETTINGS)) {
            for (Key key : Key.values()) {
                final List<KeyRoutingData> routes = context.keysRoutingTable[key.ordinal()];
                if (routes == null) {
                    continue;
                }
                for (KeyRoutingData routingData : routes) {
                    final int keyChord = KeyChord.of(routingData.mods, key);
                    IkGui.text(
                            String.format(
                                    "%s: 0x%08X (scored %d)",
                                    KeyChord.getName(keyChord),
                                    routingData.routingCurr,
                                    routingData.routingCurrScore));
                    IkGuiImplDebugTools.debugLocateItemOnHover(routingData.routingCurr);
                    if (context.io.configDebugIsDebuggerPresent) {
                        IkGui.sameLine();
                        IkGui.pushID(keyChord);
                        if (IkGuiImplDebugTools.debugBreakButton(
                                "**DebugBreak**", "in setShortcutRouting() for this KeyChord")) {
                            context.debugBreakInShortcutRouting = keyChord;
                        }
                        IkGui.popID();
                    }
                }
            }
        }
        IkGui.endChild();
        IkGui.text(
                String.format(
                        "(ActiveIdUsing: AllKeyboardKeys: %b, NavDirMask: 0x%X)",
                        context.activeIDUsingAllKeyboardKeys, context.activeIDUsingNavDirMask));
        IkGui.unindent();
        IkGui.treePop();
    }

    /** The internal state section of the metrics window. */
    private static void showInternalStateSection() {
        if (!IkGui.treeNode("Internal state")) {
            return;
        }
        IkGui.text("WINDOWING");
        IkGui.indent();
        IkGui.text("HoveredWindow: '" + windowName(context.windowHovered) + "'");
        IkGui.text(
                "HoveredWindow->Root: '"
                        + (context.windowHovered != null
                                ? context.windowHovered.rootWindowDockTree.name
                                : "NULL")
                        + "'");
        IkGui.text(
                "HoveredWindowUnderMovingWindow: '"
                        + windowName(context.windowHoveredUnderMovingWindow)
                        + "'");
        IkGui.text(
                String.format(
                        "HoveredDockNode: 0x%08X",
                        context.debugHoveredDockNode != null
                                ? context.debugHoveredDockNode.id
                                : 0));
        IkGui.text("MovingWindow: '" + windowName(context.windowMoving) + "'");
        IkGui.text(
                String.format(
                        "MouseViewport: 0x%08X (UserHovered 0x%08X, LastHovered 0x%08X)",
                        context.mouseViewport != null ? context.mouseViewport.id : 0,
                        context.io.mouseHoveredViewport,
                        context.mouseLastHoveredViewport != null
                                ? context.mouseLastHoveredViewport.id
                                : 0));
        IkGui.unindent();

        IkGui.text("ITEMS");
        IkGui.indent();
        IkGui.text(
                String.format(
                        "ActiveId: 0x%08X/0x%08X (%.2f sec), AllowOverlap: %b, Source: %s",
                        context.activeID,
                        context.activeIDPreviousFrame,
                        context.activeIDTimer / 1000.0f,
                        context.activeIDAllowOverlap,
                        context.activeIDSource));
        IkGuiImplDebugTools.debugLocateItemOnHover(context.activeID);
        IkGui.text("ActiveIdWindow: '" + windowName(context.activeIDWindow) + "'");
        IkGui.text(
                String.format(
                        "ActiveIdUsing: AllKeyboardKeys: %b, NavDirMask: %X",
                        context.activeIDUsingAllKeyboardKeys, context.activeIDUsingNavDirMask));
        // Not displaying hoveredID as it is updated mid-frame
        IkGui.text(
                String.format(
                        "HoveredId: 0x%08X (%.2f sec), AllowOverlap: %b",
                        context.hoveredIDPreviousFrame,
                        context.hoveredIDTimer / 1000.0f,
                        context.hoveredIDAllowOverlap));
        IkGui.text(
                String.format(
                        "HoverItemDelayId: 0x%08X, Timer: %.2f, ClearTimer: %.2f",
                        context.hoverItemDelayID,
                        context.hoverItemDelayTimer / 1000.0f,
                        context.hoverItemDelayClearTimer / 1000.0f));
        IkGui.text(
                String.format(
                        "DragDrop: %b, SourceId = 0x%08X, Payload \"%s\"",
                        context.dragDropActive,
                        context.dragDropPayload.sourceID,
                        context.dragDropPayload.dataType));
        IkGuiImplDebugTools.debugLocateItemOnHover(context.dragDropPayload.sourceID);
        IkGui.unindent();

        IkGui.text("NAV,FOCUS");
        IkGui.indent();
        IkGui.text("NavWindow: '" + windowName(context.navFocusedWindow) + "'");
        IkGui.text(String.format("NavId: 0x%08X, NavLayer: %d", context.navID, context.navLayer));
        IkGuiImplDebugTools.debugLocateItemOnHover(context.navID);
        IkGui.text("NavInputSource: " + context.navInputSource);
        IkGui.text(
                String.format(
                        "NavLastValidSelectionUserData = %d (0x%X)",
                        context.navLastValidSelectionUserData,
                        context.navLastValidSelectionUserData));
        IkGui.text(
                String.format(
                        "NavActive: %b, NavVisible: %b",
                        context.io.navActive, context.io.navVisible));
        IkGui.text(
                String.format(
                        "NavActivateId/DownId/PressedId: %08X/%08X/%08X",
                        context.navActivateID,
                        context.navActivateDownID,
                        context.navActivatePressedID));
        IkGui.text(String.format("NavActivateFlags: %04X", context.navActivateFlags));
        IkGui.text(
                String.format(
                        "NavCursorVisible: %b, NavHighlightItemUnderNav: %b",
                        context.navCursorVisible, context.navHighlightItemUnderNav));
        IkGui.text(String.format("NavFocusScopeId = 0x%08X", context.navFocusScopeID));
        IkGui.text("NavFocusRoute[] = ");
        for (int n = context.navFocusRoute.size() - 1; n >= 0; --n) {
            final FocusScopeData focusScope = context.navFocusRoute.get(n);
            IkGui.sameLine(0.0f, 0.0f);
            IkGui.text(String.format("0x%08X/", focusScope.id));
            final Window scopeWindow = IkGuiInternal.findWindowByID(focusScope.windowID);
            IkGui.setItemTooltip("In window \"" + windowName(scopeWindow) + "\"");
        }
        IkGui.text("NavWindowingTarget: '" + windowName(context.navWindowingTarget) + "'");
        IkGui.unindent();

        IkGui.treePop();
    }

    /**
     * The name of a window, for display.
     *
     * @param window The window, may be null.
     * @return The name, or "NULL".
     */
    private static String windowName(Window window) {
        return window != null ? window.name : "NULL";
    }

    /** Draw the overlays selected in the tools section. */
    private static void showOverlays() {
        final MetricsConfig cfg = context.debugMetricsConfig;

        // Display window rectangles and begin order
        if (cfg.showWindowsRects.get() || cfg.showWindowsBeginOrder.get()) {
            for (Window window : context.windowDisplayOrder) {
                if (!window.wasActive) {
                    continue;
                }
                final DrawList drawList = foregroundDrawList(window);
                if (cfg.showWindowsRects.get()) {
                    final RectFloat rect = getWindowRect(window, cfg.showWindowsRectsType.get());
                    drawList.addRect(
                            rect.getLeft(),
                            rect.getTop(),
                            rect.getRight(),
                            rect.getBottom(),
                            Color.rgba(255, 0, 128, 255));
                }
                if (cfg.showWindowsBeginOrder.get()
                        && (window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0) {
                    final float fontSize = IkGuiInternal.getFontSize();
                    drawList.addRectFilled(
                            window.position.x,
                            window.position.y,
                            window.position.x + fontSize,
                            window.position.y + fontSize,
                            Color.rgba(200, 100, 100, 255));
                    drawList.addText(
                            context.fontSize,
                            window.position.x,
                            window.position.y,
                            Color.WHITE,
                            Integer.toString(window.beginOrderWithinContext),
                            0.0f);
                }
            }
        }

        // Display table rectangles
        if (cfg.showTablesRects.get()) {
            for (Table table : context.tables) {
                if (table == null || table.lastFrameActive < context.frameCount - 1) {
                    continue;
                }
                final DrawList drawList = foregroundDrawList(table.outerWindow);
                final int rectType = cfg.showTablesRectsType.get();
                if (rectType >= TRT_COLUMNS_RECT) {
                    for (int column = 0; column < table.columnsCount; ++column) {
                        final RectFloat rect = getTableRect(table, rectType, column);
                        final boolean hovered = table.hoveredColumnBody == column;
                        drawList.addRect(
                                rect.getLeft(),
                                rect.getTop(),
                                rect.getRight(),
                                rect.getBottom(),
                                hovered
                                        ? Color.rgba(255, 255, 128, 255)
                                        : Color.rgba(255, 0, 128, 255),
                                0.0f,
                                DrawFlags.ROUND_CORNERS_ALL,
                                hovered ? 3.0f : 1.0f);
                    }
                } else {
                    final RectFloat rect = getTableRect(table, rectType, -1);
                    drawList.addRect(
                            rect.getLeft(),
                            rect.getTop(),
                            rect.getRight(),
                            rect.getBottom(),
                            Color.rgba(255, 0, 128, 255));
                }
            }
        }

        // Display docking info
        final DockNode node = context.debugHoveredDockNode;
        if (cfg.showDockingNodes.get() && context.io.keyCtrl && node != null) {
            final String text =
                    String.format(
                            "DockId: %X%s\nWindowClass: %08X\nSize: (%.0f, %.0f)\nSizeRef: (%.0f,"
                                    + " %.0f)",
                            node.id,
                            node.isCentralNode() ? " *CentralNode*" : "",
                            node.windowClass.classID,
                            node.size.x,
                            node.size.y,
                            node.sizeRef.x,
                            node.sizeRef.y);
            final DrawList overlayDrawList = foregroundDrawList(node.hostWindow);
            final float depth = IkGuiImplDocking.dockNodeGetDepth(node);
            overlayDrawList.addRect(
                    node.position.x + 3 * depth,
                    node.position.y + 3 * depth,
                    node.position.x + node.size.x - 3 * depth,
                    node.position.y + node.size.y - 3 * depth,
                    Color.rgba(200, 100, 100, 255));
            final float posX = node.position.x + 3 * depth;
            final float posY = node.position.y + 3 * depth;
            final Vector2f textSize = IkGui.calcTextSize(text);
            overlayDrawList.addRectFilled(
                    posX - 1,
                    posY - 1,
                    posX + textSize.x + 1,
                    posY + textSize.y + 1,
                    Color.rgba(200, 100, 100, 255));
            overlayDrawList.addText(context.fontSize, posX, posY, Color.WHITE, text, 0.0f);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Inspectors
    // ---------------------------------------------------------------------------------------------

    /**
     * Display checkboxes for dock node flags.
     *
     * @param flags The flags, modified when enabled.
     * @param label The label.
     * @param enabled Whether the flags can be edited.
     * @return The new flags.
     */
    private static int debugNodeDockNodeFlags(int flags, @NonNull String label, boolean enabled) {
        IkGui.pushID(label);
        IkGui.pushStyleVarFloat2(
                com.ikalagaming.graphics.gui.enums.StyleVariable.FRAME_PADDING, 0.0f, 0.0f);
        IkGui.text(label + ":");
        if (!enabled) {
            IkGui.beginDisabled();
        }
        final IkInt value = new IkInt(flags);
        IkGui.checkboxFlags("NoResize", value, DockNodeFlags.NO_RESIZE);
        IkGui.checkboxFlags("NoResizeX", value, DockNodeFlags.INTERNAL_NO_RESIZE_X);
        IkGui.checkboxFlags("NoResizeY", value, DockNodeFlags.INTERNAL_NO_RESIZE_Y);
        IkGui.checkboxFlags("NoTabBar", value, DockNodeFlags.INTERNAL_NO_TAB_BAR);
        IkGui.checkboxFlags("HiddenTabBar", value, DockNodeFlags.INTERNAL_HIDDEN_TAB_BAR);
        IkGui.checkboxFlags(
                "NoWindowMenuButton", value, DockNodeFlags.INTERNAL_NO_WINDOW_MENU_BUTTON);
        IkGui.checkboxFlags("NoCloseButton", value, DockNodeFlags.INTERNAL_NO_CLOSE_BUTTON);
        IkGui.checkboxFlags(
                "DockedWindowsInFocusRoute",
                value,
                DockNodeFlags.INTERNAL_DOCKED_WINDOWS_IN_FOCUS_ROUTE);
        IkGui.checkboxFlags("NoDocking", value, DockNodeFlags.INTERNAL_NO_DOCKING);
        IkGui.checkboxFlags("NoDockingSplit", value, DockNodeFlags.NO_DOCKING_SPLIT);
        IkGui.checkboxFlags(
                "NoDockingSplitOther", value, DockNodeFlags.INTERNAL_NO_DOCKING_SPLIT_OTHER);
        IkGui.checkboxFlags("NoDockingOver", value, DockNodeFlags.INTERNAL_NO_DOCKING_OVER_ME);
        IkGui.checkboxFlags(
                "NoDockingOverOther", value, DockNodeFlags.INTERNAL_NO_DOCKING_OVER_OTHER);
        IkGui.checkboxFlags(
                "NoDockingOverEmpty", value, DockNodeFlags.INTERNAL_NO_DOCKING_OVER_EMPTY);
        IkGui.checkboxFlags("NoUndocking", value, DockNodeFlags.NO_UNDOCKING);
        if (!enabled) {
            IkGui.endDisabled();
        }
        IkGui.popStyleVar();
        IkGui.popID();
        return value.get();
    }

    /**
     * Display the contents of a dock node.
     *
     * @param node The node.
     * @param label The label.
     */
    static void debugNodeDockNode(@NonNull DockNode node, @NonNull String label) {
        // Alive means submitted with the keep alive only flag, active means submitted
        final boolean isAlive = context.frameCount - node.lastFrameAlive < 2;
        final boolean isActive = context.frameCount - node.lastFrameActive < 2;
        pushInactiveColor(isAlive);
        final int treeNodeFlags = node.isFocused ? TreeNodeFlags.SELECTED : TreeNodeFlags.NONE;
        final String visibleName = node.visibleWindow != null ? node.visibleWindow.name : "NULL";
        final boolean open;
        if (!node.windows.isEmpty()) {
            open =
                    IkGui.treeNodeEx(
                            String.valueOf(node.id),
                            treeNodeFlags,
                            String.format(
                                    "%s 0x%04X%s: %d windows (vis: '%s')",
                                    label,
                                    node.id,
                                    node.isVisible ? "" : " (hidden)",
                                    node.windows.size(),
                                    visibleName));
        } else {
            final String split =
                    node.splitAxis == Axis.X
                            ? "horizontal split"
                            : node.splitAxis == Axis.Y ? "vertical split" : "empty";
            open =
                    IkGui.treeNodeEx(
                            String.valueOf(node.id),
                            treeNodeFlags,
                            String.format(
                                    "%s 0x%04X%s: %s (vis: '%s')",
                                    label,
                                    node.id,
                                    node.isVisible ? "" : " (hidden)",
                                    split,
                                    visibleName));
        }
        popInactiveColor(isAlive);
        if (isActive && IkGui.isItemHovered()) {
            final Window window = node.hostWindow != null ? node.hostWindow : node.visibleWindow;
            if (window != null) {
                highlight(window, new RectFloat(node.position, node.size));
            }
        }
        if (!open) {
            return;
        }
        IkGui.bulletText(
                String.format(
                        "Pos (%.0f,%.0f), Size (%.0f, %.0f) Ref (%.0f, %.0f)",
                        node.position.x,
                        node.position.y,
                        node.size.x,
                        node.size.y,
                        node.sizeRef.x,
                        node.sizeRef.y));
        debugNodeWindow(node.hostWindow, "HostWindow");
        debugNodeWindow(node.visibleWindow, "VisibleWindow");
        IkGui.bulletText(
                String.format(
                        "SelectedTabID: 0x%08X, LastFocusedNodeID: 0x%08X",
                        node.selectedTabID, node.lastFocusedNodeID));
        IkGui.bulletText(
                "Misc:"
                        + (node.isDockSpace() ? " IsDockSpace" : "")
                        + (node.isCentralNode() ? " IsCentralNode" : "")
                        + (isAlive ? " IsAlive" : "")
                        + (isActive ? " IsActive" : "")
                        + (node.isFocused ? " IsFocused" : "")
                        + (node.wantLockSizeOnce ? " WantLockSizeOnce" : "")
                        + (node.hasCentralNodeChild ? " HasCentralNodeChild" : ""));
        if (IkGui.treeNode(
                "flags",
                String.format(
                        "Flags Merged: 0x%04X, Local: 0x%04X, InWindows: 0x%04X, Shared: 0x%04X",
                        node.mergedFlags,
                        node.localFlags,
                        node.localFlagsInWindows,
                        node.sharedFlags))) {
            if (IkGui.beginTable("flags", 4)) {
                IkGui.tableNextColumn();
                debugNodeDockNodeFlags(node.mergedFlags, "MergedFlags", false);
                IkGui.tableNextColumn();
                node.localFlags = debugNodeDockNodeFlags(node.localFlags, "LocalFlags", true);
                IkGui.tableNextColumn();
                debugNodeDockNodeFlags(node.localFlagsInWindows, "LocalFlagsInWindows", false);
                IkGui.tableNextColumn();
                node.sharedFlags = debugNodeDockNodeFlags(node.sharedFlags, "SharedFlags", true);
                IkGui.endTable();
            }
            IkGui.treePop();
        }
        if (node.parentNode != null) {
            debugNodeDockNode(node.parentNode, "ParentNode");
        }
        if (node.childNodes[0] != null) {
            debugNodeDockNode(node.childNodes[0], "Child[0]");
        }
        if (node.childNodes[1] != null) {
            debugNodeDockNode(node.childNodes[1], "Child[1]");
        }
        if (node.tabBar != null) {
            debugNodeTabBar(node.tabBar, "TabBar");
        }
        debugNodeWindowsList(node.windows, "Windows");

        IkGui.treePop();
    }

    /**
     * Display the contents of a draw list. Both the window and viewport may be null, the viewport
     * is generally null for destroyed popups which previously owned a viewport.
     *
     * @param window The window that owns the draw list, may be null.
     * @param viewport The viewport the draw list is in, may be null.
     * @param drawList The draw list.
     * @param label The label.
     */
    static void debugNodeDrawList(
            Window window, Viewport viewport, @NonNull DrawList drawList, @NonNull String label) {
        final int commandCount = drawList.getCommandCount();
        final boolean nodeOpen =
                IkGui.treeNode(
                        String.valueOf(System.identityHashCode(drawList)),
                        String.format(
                                "%s: '%s' %d vtx, %d cmds, %d points, %d point details",
                                label,
                                drawList.windowName,
                                drawList.getVertexCount(),
                                commandCount,
                                drawList.getPointCount(),
                                drawList.getPointDetailCount()));
        if (drawList == IkGui.getWindowDrawList()) {
            IkGui.sameLine();
            // We can't display stats for the active draw list, as we don't have the data double
            // buffered
            IkGui.textColored(Color.rgba(255, 102, 102, 255), "CURRENTLY APPENDING");
            if (nodeOpen) {
                IkGui.treePop();
            }
            return;
        }

        // Render additional visuals into the top-most draw list
        final DrawList foreground =
                viewport != null ? IkGuiImplUtils.getForegroundDrawList(viewport) : null;
        if (window != null && IkGui.isItemHovered() && foreground != null) {
            foreground.addRect(
                    window.position.x,
                    window.position.y,
                    window.position.x + window.size.x,
                    window.position.y + window.size.y,
                    HIGHLIGHT_COLOR);
        }
        if (!nodeOpen) {
            return;
        }

        if (window != null && !window.wasActive) {
            IkGui.textDisabled(
                    "Warning: owning Window is inactive. This DrawList is not being rendered!");
        }

        // Not clipped like the points below: an open command is taller than one line, and the
        // clipper assumes every item has the height of the first, so the commands after an open
        // one scrolled out of view would be laid out over its contents
        for (int commandIndex = 0; commandIndex < commandCount; ++commandIndex) {
            debugNodeDrawCommand(drawList, foreground, commandIndex);
        }
        IkGui.treePop();
    }

    /**
     * Display a single SDF draw command of a draw list.
     *
     * @param drawList The draw list.
     * @param foreground The draw list to draw highlights into, may be null.
     * @param commandIndex The index of the command.
     */
    private static void debugNodeDrawCommand(
            @NonNull DrawList drawList, DrawList foreground, int commandIndex) {
        final MetricsConfig cfg = context.debugMetricsConfig;
        final ByteBuffer commands = drawList.commandBuffer;
        int offset = commandIndex * DrawData.SIZE_OF_DRAW_COMMAND;
        final int pointIndex = commands.getInt(offset);
        final int detailIndex = commands.getInt(offset + Integer.BYTES);
        final int pointCount = commands.getInt(offset + 2 * Integer.BYTES);
        final int detailCount = commands.getInt(offset + 3 * Integer.BYTES);
        final int type = commands.getInt(offset + 4 * Integer.BYTES);
        final int style = commands.getInt(offset + 5 * Integer.BYTES);
        final float stroke = commands.getFloat(offset + 6 * Integer.BYTES);

        String typeName;
        try {
            typeName = DrawList.ElementType.fromID(type).name();
        } catch (IllegalArgumentException e) {
            typeName = "??? (" + type + ")";
        }
        String styleName;
        try {
            styleName = DrawList.ElementStyle.fromID(style).name();
        } catch (IllegalArgumentException e) {
            styleName = "??? (" + style + ")";
        }

        // Each command has a quad covering it in the vertex buffer
        final Vector2f[] quad = new Vector2f[6];
        final RectFloat bounds =
                new RectFloat(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        final int vertexStart = commandIndex * 6;
        final boolean hasQuad = vertexStart + 6 <= drawList.getVertexCount();
        if (hasQuad) {
            for (int v = 0; v < 6; ++v) {
                final int vertexOffset = (vertexStart + v) * DrawData.SIZE_OF_VERTEX;
                quad[v] =
                        new Vector2f(
                                drawList.vertexBuffer.getFloat(vertexOffset),
                                drawList.vertexBuffer.getFloat(vertexOffset + Float.BYTES));
                bounds.add(quad[v].x, quad[v].y);
            }
        }

        final boolean open =
                IkGui.treeNode(
                        String.valueOf(commandIndex),
                        String.format(
                                "DrawCmd %4d: %s %s, %d points, %d details, quad (%4.0f,%4.0f)-(%4.0f,%4.0f)",
                                commandIndex,
                                typeName,
                                styleName,
                                pointCount,
                                detailCount,
                                hasQuad ? bounds.getLeft() : 0,
                                hasQuad ? bounds.getTop() : 0,
                                hasQuad ? bounds.getRight() : 0,
                                hasQuad ? bounds.getBottom() : 0));
        if (IkGui.isItemHovered()
                && hasQuad
                && foreground != null
                && (cfg.showDrawCmdMesh.get() || cfg.showDrawCmdBoundingBoxes.get())) {
            debugNodeDrawCmdShowMeshAndBoundingBox(
                    foreground,
                    quad,
                    bounds,
                    cfg.showDrawCmdMesh.get(),
                    cfg.showDrawCmdBoundingBoxes.get());
        }
        if (!open) {
            return;
        }

        IkGui.text(String.format("Point Index: %d, Detail Index: %d", pointIndex, detailIndex));
        IkGui.text(String.format("Stroke: %f", stroke));
        final ByteBuffer points = drawList.pointBuffer;
        // Coarsely clip the lines of points and details, since there can be many of them. Each
        // one is a single line, so they all have the same height.
        final int shownPoints =
                Math.max(0, Math.min(pointCount, drawList.getPointCount() - pointIndex));
        final ListClipper pointClipper = new ListClipper();
        pointClipper.begin(shownPoints);
        while (pointClipper.step()) {
            for (int p = pointClipper.displayStart; p < pointClipper.displayEnd; ++p) {
                final int pointOffset = (pointIndex + p) * DrawData.SIZE_OF_POINT;
                IkGui.bulletText(
                        String.format(
                                "Point %d: (%.2f, %.2f) misc (%.2f, %.2f)",
                                p,
                                points.getFloat(pointOffset),
                                points.getFloat(pointOffset + Float.BYTES),
                                points.getFloat(pointOffset + 2 * Float.BYTES),
                                points.getFloat(pointOffset + 3 * Float.BYTES)));
            }
        }
        final ByteBuffer details = drawList.pointDetailBuffer;
        final int shownDetails =
                Math.max(0, Math.min(detailCount, drawList.getPointDetailCount() - detailIndex));
        final ListClipper detailClipper = new ListClipper();
        detailClipper.begin(shownDetails);
        while (detailClipper.step()) {
            for (int d = detailClipper.displayStart; d < detailClipper.displayEnd; ++d) {
                final int detailOffset = (detailIndex + d) * DrawData.SIZE_OF_POINT_DETAIL;
                final int colorOrTexture = details.getInt(detailOffset + 2 * Float.BYTES);
                final String textureDescription =
                        style == DrawList.ElementStyle.TEXTURE.ordinal()
                                        && colorOrTexture >= 0
                                        && colorOrTexture < context.drawTextures.textures.size()
                                ? " (texture #" + colorOrTexture + ")"
                                : "";
                IkGui.bulletText(
                        String.format(
                                "Detail %d: radius %.2f, alpha radius %.2f, color/texture 0x%08X%s,"
                                        + " tint 0x%08X",
                                d,
                                details.getFloat(detailOffset),
                                details.getFloat(detailOffset + Float.BYTES),
                                colorOrTexture,
                                textureDescription,
                                details.getInt(detailOffset + 2 * Float.BYTES + Integer.BYTES)));
            }
        }
        if (hasQuad) {
            IkGui.selectable(
                    String.format(
                            "Quad: (%.2f,%.2f) (%.2f,%.2f) (%.2f,%.2f) / (%.2f,%.2f) (%.2f,%.2f)"
                                    + " (%.2f,%.2f)",
                            quad[0].x, quad[0].y, quad[1].x, quad[1].y, quad[2].x, quad[2].y,
                            quad[3].x, quad[3].y, quad[4].x, quad[4].y, quad[5].x, quad[5].y));
            if (IkGui.isItemHovered() && foreground != null) {
                debugNodeDrawCmdShowMeshAndBoundingBox(foreground, quad, bounds, true, false);
            }
        }
        IkGui.treePop();
    }

    /**
     * Draw the quad and/or bounding box of a draw command.
     *
     * @param outDrawList The draw list to draw into.
     * @param quad The six vertices of the two triangles of the quad.
     * @param bounds The bounding box of the quad.
     * @param showMesh Whether to draw the triangles.
     * @param showBoundingBox Whether to draw the bounding box.
     */
    static void debugNodeDrawCmdShowMeshAndBoundingBox(
            @NonNull DrawList outDrawList,
            @NonNull Vector2f[] quad,
            @NonNull RectFloat bounds,
            boolean showMesh,
            boolean showBoundingBox) {
        // In yellow: the triangles of the quad
        if (showMesh) {
            outDrawList.addPolyline(
                    new Vector2f[] {quad[0], quad[1], quad[2]}, 3, HIGHLIGHT_COLOR, true, 1.0f);
            outDrawList.addPolyline(
                    new Vector2f[] {quad[3], quad[4], quad[5]}, 3, HIGHLIGHT_COLOR, true, 1.0f);
        }
        // In cyan: the bounding box of the quad
        if (showBoundingBox) {
            outDrawList.addRect(
                    IkGuiInternal.truncate(bounds.getLeft()),
                    IkGuiInternal.truncate(bounds.getTop()),
                    IkGuiInternal.truncate(bounds.getRight()),
                    IkGuiInternal.truncate(bounds.getBottom()),
                    Color.rgba(0, 255, 255, 255));
        }
    }

    /**
     * Display the contents of a storage.
     *
     * @param storage The storage.
     * @param label The label.
     */
    static void debugNodeStorage(@NonNull Storage storage, @NonNull String label) {
        final int count =
                storage.getIntEntries().size()
                        + storage.getFloatEntries().size()
                        + storage.getObjectEntries().size();
        if (!IkGui.treeNode(label, label + ": " + count + " entries")) {
            return;
        }
        storage.getIntEntries()
                .forEach(
                        (key, value) -> {
                            IkGui.bulletText(
                                    String.format("Key 0x%08X Value { i: %d }", key, value));
                            IkGuiImplDebugTools.debugLocateItemOnHover(key);
                        });
        storage.getFloatEntries()
                .forEach(
                        (key, value) -> {
                            IkGui.bulletText(
                                    String.format("Key 0x%08X Value { f: %f }", key, value));
                            IkGuiImplDebugTools.debugLocateItemOnHover(key);
                        });
        storage.getObjectEntries()
                .forEach(
                        (key, value) -> {
                            IkGui.bulletText(
                                    String.format(
                                            "Key 0x%08X Value { %s }",
                                            key,
                                            value != null
                                                    ? value.getClass().getSimpleName()
                                                    : "null"));
                            IkGuiImplDebugTools.debugLocateItemOnHover(key);
                        });
        IkGui.treePop();
    }

    /**
     * Display the contents of a tab bar.
     *
     * @param tabBar The tab bar.
     * @param label The label.
     */
    static void debugNodeTabBar(@NonNull TabBar tabBar, @NonNull String label) {
        // Standalone tab bars (not associated with docking/windows) hold no discernible strings
        final boolean isActive = tabBar.previousFrameVisible >= context.frameCount - 2;
        final StringBuilder description =
                new StringBuilder(
                        String.format(
                                "%s 0x%08X (%d tabs)%s  {",
                                label,
                                tabBar.id,
                                tabBar.tabs.size(),
                                isActive ? "" : " *Inactive*"));
        for (int n = 0; n < Math.min(tabBar.tabs.size(), 3); ++n) {
            description
                    .append(n > 0 ? ", " : "")
                    .append('\'')
                    .append(IkGuiImplTabs.tabBarGetTabName(tabBar.tabs.get(n)))
                    .append('\'');
        }
        description.append(tabBar.tabs.size() > 3 ? " ... }" : " } ");
        pushInactiveColor(isActive);
        final boolean open = IkGui.treeNode(label, description.toString());
        popInactiveColor(isActive);
        if (isActive && IkGui.isItemHovered()) {
            final DrawList drawList = foregroundDrawList(tabBar.window);
            final RectFloat bar = tabBar.barRect;
            drawList.addRect(
                    bar.getLeft(), bar.getTop(), bar.getRight(), bar.getBottom(), HIGHLIGHT_COLOR);
            drawList.addLineV(
                    tabBar.scrollingRectMinX,
                    bar.getTop(),
                    bar.getBottom(),
                    Color.rgba(0, 255, 0, 255),
                    1.0f);
            drawList.addLineV(
                    tabBar.scrollingRectMaxX,
                    bar.getTop(),
                    bar.getBottom(),
                    Color.rgba(0, 255, 0, 255),
                    1.0f);
        }
        if (!open) {
            return;
        }
        for (int n = 0; n < tabBar.tabs.size(); ++n) {
            final TabItem tab = tabBar.tabs.get(n);
            IkGui.pushID(tab.id);
            if (IkGui.smallButton("<")) {
                IkGuiImplTabs.tabBarQueueReorder(tabBar, tab, -1);
            }
            IkGui.sameLine(0, 2);
            if (IkGui.smallButton(">")) {
                IkGuiImplTabs.tabBarQueueReorder(tabBar, tab, +1);
            }
            IkGui.sameLine();
            IkGui.text(
                    String.format(
                            "%02d%c Tab 0x%08X '%s' Offset: %.2f, Width: %.2f/%.2f",
                            n,
                            tab.id == tabBar.selectedTabID ? '*' : ' ',
                            tab.id,
                            IkGuiImplTabs.tabBarGetTabName(tab),
                            tab.offset,
                            tab.width,
                            tab.contentWidth));
            IkGui.popID();
        }
        IkGui.treePop();
    }

    /**
     * Display the contents of a viewport.
     *
     * @param viewport The viewport.
     */
    static void debugNodeViewport(@NonNull Viewport viewport) {
        IkGui.setNextItemOpen(true, Condition.ONCE);
        final boolean open =
                IkGui.treeNode(
                        String.valueOf(viewport.id),
                        String.format(
                                "Viewport #%d, ID: 0x%08X, Parent: 0x%08X, Window: \"%s\"",
                                viewport.index,
                                viewport.id,
                                viewport.parentViewportID,
                                viewport.window != null ? viewport.window.name : "N/A"));
        if (IkGui.isItemHovered()) {
            context.debugMetricsConfig.highlightViewportID = viewport.id;
        }
        if (!open) {
            return;
        }
        final int flags = viewport.flags;
        IkGui.bulletText(
                String.format(
                        "Main Pos: (%.0f,%.0f), Size: (%.0f,%.0f)\nFrameBufferScale: (%.2f,%.2f)\n"
                                + "WorkArea Inset Left: %.0f Top: %.0f, Right: %.0f, Bottom: %.0f\n"
                                + "Monitor: %d, DpiScale: %.0f%%",
                        viewport.position.x,
                        viewport.position.y,
                        viewport.size.x,
                        viewport.size.y,
                        viewport.framebufferScale.x,
                        viewport.framebufferScale.y,
                        viewport.workInsetMin.x,
                        viewport.workInsetMin.y,
                        viewport.workInsetMax.x,
                        viewport.workInsetMax.y,
                        viewport.platformMonitor,
                        viewport.dpiScale * 100.0f));
        if (viewport.index > 0) {
            IkGui.sameLine();
            if (IkGui.smallButton("Reset Pos")) {
                viewport.position.set(200, 200);
                viewport.updateWorkRect();
                if (viewport.window != null) {
                    viewport.window.position.set(viewport.position);
                }
            }
        }
        IkGui.bulletText(
                String.format("Flags: 0x%04X =", flags)
                        + ((flags & ViewportFlags.IS_PLATFORM_MONITOR) != 0
                                ? " IsPlatformMonitor"
                                : "")
                        + ((flags & ViewportFlags.IS_MINIMIZED) != 0 ? " IsMinimized" : "")
                        + ((flags & ViewportFlags.IS_FOCUSED) != 0 ? " IsFocused" : "")
                        + ((flags & ViewportFlags.OWNED_BY_APP) != 0 ? " OwnedByApp" : "")
                        + ((flags & ViewportFlags.NO_DECORATION) != 0 ? " NoDecoration" : "")
                        + ((flags & ViewportFlags.NO_TASK_BAR_ICON) != 0 ? " NoTaskBarIcon" : "")
                        + ((flags & ViewportFlags.NO_FOCUS_ON_APPEARING) != 0
                                ? " NoFocusOnAppearing"
                                : "")
                        + ((flags & ViewportFlags.NO_FOCUS_ON_CLICK) != 0 ? " NoFocusOnClick" : "")
                        + ((flags & ViewportFlags.NO_INPUTS) != 0 ? " NoInputs" : "")
                        + ((flags & ViewportFlags.NO_RENDERER_CLEAR) != 0 ? " NoRendererClear" : "")
                        + ((flags & ViewportFlags.NO_AUTO_MERGE) != 0 ? " NoAutoMerge" : "")
                        + ((flags & ViewportFlags.TOP_MOST) != 0 ? " TopMost" : "")
                        + ((flags & ViewportFlags.CAN_HOST_OTHER_WINDOWS) != 0
                                ? " CanHostOtherWindows"
                                : ""));
        for (DrawList drawList : new ArrayList<>(viewport.drawData.drawLists)) {
            debugNodeDrawList(null, viewport, drawList, "DrawList");
        }
        IkGui.treePop();
    }

    /**
     * Display the bounds of a monitor.
     *
     * @param monitor The monitor.
     * @param label The label.
     * @param index The index of the monitor.
     */
    static void debugNodePlatformMonitor(
            @NonNull PlatformMonitor monitor, @NonNull String label, int index) {
        IkGui.bulletText(
                String.format(
                        "%s %d: DPI %.0f%%\n MainMin (%.0f,%.0f), MainMax (%.0f,%.0f), MainSize"
                                + " (%.0f,%.0f)\n WorkMin (%.0f,%.0f), WorkMax (%.0f,%.0f),"
                                + " WorkSize (%.0f,%.0f)",
                        label,
                        index,
                        monitor.dpiScale * 100.0f,
                        monitor.mainPosition.x,
                        monitor.mainPosition.y,
                        monitor.mainPosition.x + monitor.mainSize.x,
                        monitor.mainPosition.y + monitor.mainSize.y,
                        monitor.mainSize.x,
                        monitor.mainSize.y,
                        monitor.workPosition.x,
                        monitor.workPosition.y,
                        monitor.workPosition.x + monitor.workSize.x,
                        monitor.workPosition.y + monitor.workSize.y,
                        monitor.workSize.x,
                        monitor.workSize.y));
    }

    /**
     * Display the contents of a window.
     *
     * @param window The window, may be null.
     * @param label The label.
     */
    static void debugNodeWindow(Window window, @NonNull String label) {
        if (window == null) {
            IkGui.bulletText(label + ": NULL");
            return;
        }

        final boolean isActive = window.wasActive;
        final int treeNodeFlags =
                window == context.navFocusedWindow ? TreeNodeFlags.SELECTED : TreeNodeFlags.NONE;
        pushInactiveColor(isActive);
        final boolean open =
                IkGui.treeNodeEx(
                        label,
                        treeNodeFlags,
                        String.format(
                                "%s '%s'%s", label, window.name, isActive ? "" : " *Inactive*"));
        popInactiveColor(isActive);
        if (IkGui.isItemHovered() && isActive) {
            highlight(window, window.getRect());
        }
        if (!open) {
            return;
        }

        if (context.io.configDebugIsDebuggerPresent
                && IkGuiImplDebugTools.debugBreakButton("**DebugBreak**", "in begin()")) {
            context.debugBreakInWindow = window.id;
        }

        final int flags = window.flags;
        debugNodeDrawList(window, window.viewport, window.drawList, "DrawList");
        IkGui.bulletText(
                String.format(
                        "Pos: (%.1f,%.1f), Size: (%.1f,%.1f), ContentSize (%.1f,%.1f) Ideal"
                                + " (%.1f,%.1f)",
                        window.position.x,
                        window.position.y,
                        window.size.x,
                        window.size.y,
                        window.contentSize.x,
                        window.contentSize.y,
                        window.contentSizeIdeal.x,
                        window.contentSizeIdeal.y));
        IkGui.bulletText(
                String.format("Flags: 0x%08X (", flags)
                        + ((flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0 ? "Child " : "")
                        + ((flags & WindowFlags.INTERNAL_TOOLTIP) != 0 ? "Tooltip " : "")
                        + ((flags & WindowFlags.INTERNAL_POPUP) != 0 ? "Popup " : "")
                        + ((flags & WindowFlags.INTERNAL_MODAL) != 0 ? "Modal " : "")
                        + ((flags & WindowFlags.INTERNAL_CHILD_MENU) != 0 ? "ChildMenu " : "")
                        + ((flags & WindowFlags.NO_SAVED_SETTINGS) != 0 ? "NoSavedSettings " : "")
                        + ((flags & WindowFlags.NO_MOUSE_INPUTS) != 0 ? "NoMouseInputs " : "")
                        + ((flags & WindowFlags.NO_NAV_INPUTS) != 0 ? "NoNavInputs " : "")
                        + ((flags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0 ? "AlwaysAutoResize " : "")
                        + "..)");
        if ((flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0) {
            final int childFlags = window.flagsAsChildWindow;
            IkGui.bulletText(
                    String.format("ChildFlags: 0x%08X (", childFlags)
                            + ((childFlags & ChildFlags.BORDERS) != 0 ? "Borders " : "")
                            + ((childFlags & ChildFlags.RESIZE_X) != 0 ? "ResizeX " : "")
                            + ((childFlags & ChildFlags.RESIZE_Y) != 0 ? "ResizeY " : "")
                            + ((childFlags & ChildFlags.NAV_FLATTENED) != 0 ? "NavFlattened " : "")
                            + "..)");
        }
        IkGui.bulletText(String.format("WindowClassId: 0x%08X", window.windowClass.classID));
        IkGui.bulletText(
                String.format(
                        "Scroll: (%.2f/%.2f,%.2f/%.2f) Scrollbar:%s%s",
                        window.scrollPosition.x,
                        window.scrollMax.x,
                        window.scrollPosition.y,
                        window.scrollMax.y,
                        window.scrollbarX ? "X" : "",
                        window.scrollbarY ? "Y" : ""));
        IkGui.bulletText(
                String.format(
                        "Active: %b/%b, WriteAccessed: %b, BeginOrderWithinContext: %d",
                        window.active,
                        window.wasActive,
                        window.writeAccessed,
                        (window.active || window.wasActive) ? window.beginOrderWithinContext : -1));
        IkGui.bulletText(
                String.format(
                        "Appearing: %b, Hidden: %b (CanSkip %d Cannot %d), SkipItems: %b",
                        window.appearing,
                        window.hidden,
                        window.hiddenFramesCanSkipItems.get(),
                        window.hiddenFramesCannotSkipItems.get(),
                        window.skipItems));
        for (int layer = 0; layer < window.navLastIDs.length; ++layer) {
            final RectFloat rect = window.navRectRelative[layer];
            if (rect.getLeft() >= rect.getRight() && rect.getTop() >= rect.getBottom()) {
                IkGui.bulletText(
                        String.format("NavLastIds[%d]: 0x%08X", layer, window.navLastIDs[layer]));
            } else {
                IkGui.bulletText(
                        String.format(
                                "NavLastIds[%d]: 0x%08X at +(%.1f,%.1f)(%.1f,%.1f)",
                                layer,
                                window.navLastIDs[layer],
                                rect.getLeft(),
                                rect.getTop(),
                                rect.getRight(),
                                rect.getBottom()));
            }
            IkGuiImplDebugTools.debugLocateItemOnHover(window.navLastIDs[layer]);
        }
        for (int layer = 0; layer < window.navPreferredScoringPositionRelative.length; ++layer) {
            final Vector2f position = window.navPreferredScoringPositionRelative[layer];
            // Display as -99999 so it looks neater
            IkGui.bulletText(
                    String.format(
                            "NavPreferredScoringPosRel[%d] = (%.1f,%.1f)",
                            layer,
                            position.x == Float.MAX_VALUE ? -99_999.0f : position.x,
                            position.y == Float.MAX_VALUE ? -99_999.0f : position.y));
        }
        IkGui.bulletText(
                String.format(
                        "NavLayersActiveMask: %X, NavLastChildNavWindow: %s",
                        window.navLayersActiveMask, windowName(window.navLastChildNavWindow)));

        IkGui.bulletText(
                String.format(
                        "Viewport: %d%s, ViewportId: 0x%08X, ViewportPos: (%.1f,%.1f)",
                        window.viewport != null ? window.viewport.index : -1,
                        window.viewportOwned ? " (Owned)" : "",
                        window.viewportID,
                        window.viewportPosition.x,
                        window.viewportPosition.y));
        IkGui.bulletText(
                "ViewportMonitor: "
                        + (window.viewport != null ? window.viewport.platformMonitor : -1));
        IkGui.bulletText(
                String.format(
                        "DockId: 0x%04X, DockOrder: %d, Act: %b, Vis: %b",
                        window.dockID,
                        window.dockOrder,
                        window.dockIsActive,
                        window.dockTabIsVisible));
        if (window.dockNode != null || window.dockNodeAsHost != null) {
            debugNodeDockNode(
                    window.dockNodeAsHost != null ? window.dockNodeAsHost : window.dockNode,
                    window.dockNodeAsHost != null ? "DockNodeAsHost" : "DockNode");
        }

        if (window.rootWindow != window) {
            debugNodeWindow(window.rootWindow, "RootWindow");
        }
        if (window.rootWindowDockTree != window.rootWindow) {
            debugNodeWindow(window.rootWindowDockTree, "RootWindowDockTree");
        }
        if (window.parentWindow != null) {
            debugNodeWindow(window.parentWindow, "ParentWindow");
        }
        if (window.parentWindowForFocusRoute != null) {
            debugNodeWindow(window.parentWindowForFocusRoute, "ParentWindowForFocusRoute");
        }
        if (!window.childWindows.isEmpty()) {
            debugNodeWindowsList(window.childWindows, "ChildWindows");
        }
        debugNodeStorage(window.stateStorage, "Storage");
        IkGui.treePop();
    }

    /**
     * Display a window settings entry.
     *
     * @param settings The settings.
     */
    static void debugNodeWindowSettings(@NonNull WindowSettings settings) {
        if (settings.wantDelete) {
            IkGui.beginDisabled();
        }
        IkGui.bulletText(
                String.format(
                        "0x%08X \"%s\" Pos (%d,%d) Size (%d,%d) Collapsed=%b",
                        settings.id,
                        settings.name,
                        settings.position.x,
                        settings.position.y,
                        settings.size.x,
                        settings.size.y,
                        settings.collapsed));
        if (settings.wantDelete) {
            IkGui.endDisabled();
        }
    }

    /**
     * Display a list of windows, front to back.
     *
     * @param windows The windows.
     * @param label The label.
     */
    static void debugNodeWindowsList(@NonNull List<Window> windows, @NonNull String label) {
        if (!IkGui.treeNode(label, label + " (" + windows.size() + ")")) {
            return;
        }
        // Iterate front to back
        final List<Window> copy = new ArrayList<>(windows);
        for (int i = copy.size() - 1; i >= 0; --i) {
            IkGui.pushID(copy.get(i).id);
            debugNodeWindow(copy.get(i), "Window");
            IkGui.popID();
        }
        IkGui.treePop();
    }

    /**
     * Display windows in their begin stack hierarchy, starting at a given index.
     *
     * @param windows The windows, sorted by begin order.
     * @param start The index to start searching at.
     * @param parentInBeginStack The parent we want the children of, or null for root windows.
     */
    static void debugNodeWindowsListByBeginStackParent(
            @NonNull List<Window> windows, int start, Window parentInBeginStack) {
        for (int i = start; i < windows.size(); ++i) {
            final Window window = windows.get(i);
            if (window.parentWindowInBeginStack != parentInBeginStack) {
                continue;
            }
            final String label = String.format("[%04d] Window", window.beginOrderWithinContext);
            debugNodeWindow(window, label);
            IkGui.treePush(label);
            debugNodeWindowsListByBeginStackParent(windows, i + 1, window);
            IkGui.treePop();
        }
    }

    /**
     * Describe the sizing policy of a table.
     *
     * @param tableFlags The table flags.
     * @return The name of the sizing policy.
     */
    private static String debugNodeTableGetSizingPolicyDescription(int tableFlags) {
        final int sizingPolicy = tableFlags & TableFlags.INTERNAL_SIZING_MASK;
        if (sizingPolicy == TableFlags.SIZING_FIXED_FIT) {
            return "FixedFit";
        }
        if (sizingPolicy == TableFlags.SIZING_FIXED_SAME) {
            return "FixedSame";
        }
        if (sizingPolicy == TableFlags.SIZING_STRETCH_PROP) {
            return "StretchProp";
        }
        if (sizingPolicy == TableFlags.SIZING_STRETCH_SAME) {
            return "StretchSame";
        }
        return "N/A";
    }

    /**
     * Display the contents of a table.
     *
     * @param table The table.
     */
    static void debugNodeTable(@NonNull Table table) {
        // Note that fully clipped early out scrolling tables will appear as inactive here
        final boolean isActive = table.lastFrameActive >= context.frameCount - 2;
        pushInactiveColor(isActive);
        final boolean open =
                IkGui.treeNode(
                        String.valueOf(table.id),
                        String.format(
                                "Table 0x%08X (%d columns, in '%s')%s",
                                table.id,
                                table.columnsCount,
                                windowName(table.outerWindow),
                                isActive ? "" : " *Inactive*"));
        popInactiveColor(isActive);
        if (IkGui.isItemHovered()) {
            highlight(table.outerWindow, table.outerRect);
        }
        if (IkGui.isItemVisible() && table.hoveredColumnBody != -1) {
            final Vector2f min = IkGui.getItemRectMin();
            final Vector2f max = IkGui.getItemRectMax();
            highlight(table.outerWindow, new RectFloat(min.x, min.y, max.x, max.y));
        }
        if (!open) {
            return;
        }
        if (table.instanceCurrent > 0) {
            IkGui.text(
                    "** "
                            + (table.instanceCurrent + 1)
                            + " instances of same table! Some data below will refer to last"
                            + " instance.");
        }
        if (context.io.configDebugIsDebuggerPresent) {
            if (IkGuiImplDebugTools.debugBreakButton("**DebugBreak**", "in beginTable()")) {
                context.debugBreakInTable = table.id;
            }
            IkGui.sameLine();
        }

        final boolean clearSettings = IkGui.smallButton("Clear settings");
        IkGui.bulletText(
                String.format(
                        "OuterRect: Pos: (%.1f,%.1f) Size: (%.1f,%.1f) Sizing: '%s'",
                        table.outerRect.getLeft(),
                        table.outerRect.getTop(),
                        table.outerRect.getWidth(),
                        table.outerRect.getHeight(),
                        debugNodeTableGetSizingPolicyDescription(table.flags)));
        IkGui.bulletText(
                String.format(
                        "ColumnsGivenWidth: %.1f, ColumnsAutoFitWidth: %.1f, InnerWidth: %.1f%s",
                        table.columnsGivenWidth,
                        table.columnsAutoFitWidth,
                        table.innerWidth,
                        table.innerWidth == 0.0f ? " (auto)" : ""));
        IkGui.bulletText(
                String.format(
                        "CellPaddingX: %.1f, CellSpacingX: %.1f/%.1f, OuterPaddingX: %.1f",
                        table.cellPaddingX,
                        table.cellSpacingX1,
                        table.cellSpacingX2,
                        table.outerPaddingX));
        IkGui.bulletText(
                String.format(
                        "HoveredColumnBody: %d, HoveredColumnBorder: %d",
                        table.hoveredColumnBody, table.hoveredColumnBorder));
        IkGui.bulletText(
                String.format(
                        "ResizedColumn: %d, HeldHeaderColumn: %d, ReorderColumn: %d",
                        table.lastResizedColumn, table.lastHeldHeaderColumn, table.reorderColumn));
        for (int n = 0; n < table.instanceCurrent + 1; ++n) {
            final TableInstanceData instance = table.getInstanceData(n);
            IkGui.bulletText(
                    String.format(
                            "Instance %d: HoveredRow: %d, LastOuterHeight: %.2f",
                            n, instance.hoveredRowLast, instance.lastOuterHeight));
        }
        float sumWeights = 0.0f;
        for (int n = 0; n < table.columnsCount; ++n) {
            if ((table.columns[n].flags & TableColumnFlags.WIDTH_STRETCH) != 0) {
                sumWeights += table.columns[n].stretchWeight;
            }
        }
        for (int n = 0; n < table.columnsCount; ++n) {
            final TableColumn column = table.columns[n];
            final String name = IkGuiImplTables.tableGetColumnName(table, n);
            final String description =
                    String.format(
                            "Column %d order %d '%s': offset %+.2f to %+.2f%s\n"
                                    + "Enabled: %b, VisibleX/Y: %b/%b, RequestOutput: %b,"
                                    + " SkipItems: %b, DrawChannels: %d,%d\n"
                                    + "WidthGiven: %.1f, Request/Auto: %.1f/%.1f, StretchWeight:"
                                    + " %.3f (%.1f%%)\n"
                                    + "MinX: %.1f, MaxX: %.1f (%+.1f), ClipRect: %.1f to %.1f"
                                    + " (+%.1f)\n"
                                    + "ContentWidth: %.1f,%.1f, HeadersUsed/Ideal %.1f/%.1f\n"
                                    + "Sort: %d%s, UserData: 0x%08X, Flags: 0x%04X: %s%s%s..",
                            n,
                            column.displayOrder,
                            name != null ? name : "",
                            column.minX - table.workRect.getLeft(),
                            column.maxX - table.workRect.getLeft(),
                            n < table.freezeColumnsRequest ? " (Frozen)" : "",
                            column.isEnabled,
                            column.isVisibleX,
                            column.isVisibleY,
                            column.isRequestOutput,
                            column.isSkipItems,
                            column.drawChannelFrozen,
                            column.drawChannelUnfrozen,
                            column.widthGiven,
                            column.widthRequest,
                            column.widthAuto,
                            column.stretchWeight,
                            column.stretchWeight > 0.0f
                                    ? (column.stretchWeight / sumWeights) * 100.0f
                                    : 0.0f,
                            column.minX,
                            column.maxX,
                            column.maxX - column.minX,
                            column.clipRect.getLeft(),
                            column.clipRect.getRight(),
                            column.clipRect.getWidth(),
                            column.contentMaxXFrozen - column.workMinX,
                            column.contentMaxXUnfrozen - column.workMinX,
                            column.contentMaxXHeadersUsed - column.workMinX,
                            column.contentMaxXHeadersIdeal - column.workMinX,
                            column.sortOrder,
                            column.sortDirection == SortDirection.ASCENDING
                                    ? " (Asc)"
                                    : column.sortDirection == SortDirection.DESCENDING
                                            ? " (Des)"
                                            : "",
                            column.userData,
                            column.flags,
                            (column.flags & TableColumnFlags.WIDTH_STRETCH) != 0
                                    ? "WidthStretch "
                                    : "",
                            (column.flags & TableColumnFlags.WIDTH_FIXED) != 0 ? "WidthFixed " : "",
                            (column.flags & TableColumnFlags.NO_RESIZE) != 0 ? "NoResize " : "");
            IkGui.bullet();
            IkGui.selectable(description);
            if (IkGui.isItemHovered()) {
                highlight(
                        table.outerWindow,
                        new RectFloat(
                                column.minX,
                                table.outerRect.getTop(),
                                column.maxX,
                                table.outerRect.getBottom()));
            }
        }
        final TableSettings settings = IkGuiImplTableSettings.tableGetBoundSettings(table);
        if (settings != null) {
            debugNodeTableSettings(settings, table);
        }
        if (clearSettings) {
            // Queue a call to reset the settings
            table.isResetAllRequest = true;
        }
        IkGui.treePop();
    }

    /**
     * Display a table settings entry.
     *
     * @param settings The settings.
     * @param table The table the settings belong to, or null to look it up when hovered.
     */
    static void debugNodeTableSettings(@NonNull TableSettings settings, Table table) {
        if (settings.id == 0) {
            IkGui.pushID(System.identityHashCode(settings));
        }
        final boolean open =
                IkGui.treeNode(
                        String.valueOf(settings.id),
                        String.format(
                                "Settings 0x%08X (%d columns)",
                                settings.id, settings.columnsCount));
        final boolean hovered = IkGui.isItemHovered();
        if (hovered && table == null && settings.id != 0) {
            table = IkGuiImplTables.tableFindByID(settings.id);
        }
        if (hovered && table != null) {
            highlight(table.outerWindow, table.outerRect);
        }
        if (open) {
            IkGui.bulletText(String.format("SaveFlags: 0x%08X", settings.saveFlags));
            IkGui.bulletText(
                    String.format(
                            "ColumnsCount: %d (max %d)",
                            settings.columnsCount, settings.columnsCountMax));
            for (int n = 0; n < settings.columnsCount && n < settings.columns.length; ++n) {
                final TableColumnSettings column = settings.columns[n];
                final String sort =
                        column.sortOrder == -1
                                ? "---"
                                : column.sortDirection == SortDirection.ASCENDING
                                        ? "Asc"
                                        : column.sortDirection == SortDirection.DESCENDING
                                                ? "Des"
                                                : "---";
                IkGui.bulletText(
                        String.format(
                                "Column %d Order %d SortOrder %2d %s Vis %b %s %7.3f ID 0x%08X",
                                n,
                                column.displayOrder,
                                column.sortOrder,
                                sort,
                                column.isEnabled,
                                column.isStretch ? "Weight" : "Width ",
                                column.widthOrWeight,
                                column.id));
            }
            IkGui.treePop();
        }
        if (settings.id == 0) {
            IkGui.popID();
        }
    }

    /**
     * Display the state of the active text input.
     *
     * @param state The input text state.
     */
    static void debugNodeInputTextState(@NonNull InputTextState state) {
        IkGui.text(String.format("ID: 0x%08X, ActiveID: 0x%08X", state.id, context.activeID));
        IkGuiImplDebugTools.debugLocateItemOnHover(state.id);
        final int textLength = state.text != null ? state.text.length() : 0;
        IkGui.text(
                String.format(
                        "TextLen: %d, Cursor: %d, Selection: %d..%d",
                        textLength, state.cursor, state.selectStart, state.selectEnd));
        IkGui.text(
                String.format(
                        "BufCapacity: %d, LineCount: %d", state.bufCapacity, state.lineCount));
        IkGui.text(
                String.format("has_preferred_x: %b (%.2f)", state.hasPreferredX, state.preferredX));
        IkGui.text(
                String.format(
                        "Undo records: %d, Redo records: %d, Stored characters: %d",
                        state.getUndoRecordCount(),
                        state.getRedoRecordCount(),
                        state.getUndoCharCount()));
    }
}
