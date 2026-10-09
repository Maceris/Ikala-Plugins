package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.MathUtil;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.Arrays;

/**
 * Tables. Typical call flow:
 *
 * <ul>
 *   <li>beginTable(): user begins into a table.
 *   <li>tableSetupColumn() / tableSetupScrollFreeze(): user submits column details (optional).
 *   <li>tableUpdateLayout(): sets up widths, column positions, and clip rects. Automatically called
 *       by the first call to tableNextRow() or tableHeadersRow().
 *   <li>tableHeadersRow() or tableHeader(): user submits a header row (optional).
 *   <li>tableNextRow(): user begins into a new row.
 *   <li>tableSetColumnIndex() / tableNextColumn(): user begins into a cell, and emits contents.
 *   <li>endTable(): user ends the table.
 * </ul>
 *
 * <p>Unlike ImGui, we don't reorder draw channels to merge draw calls, since each draw command
 * carries its own clip rect. Draw channels are only used to control the drawing order.
 */
@Slf4j
class IkGuiImplTables {
    /** The draw channel for row/cell backgrounds and borders. */
    static final int TABLE_DRAW_CHANNEL_BG0 = 0;

    /** The draw channel for widgets drawing across columns in frozen rows. */
    static final int TABLE_DRAW_CHANNEL_BG2_FROZEN = 1;

    /** The draw channel used with TableFlags.NO_CLIP, the last visible channel. */
    static final int TABLE_DRAW_CHANNEL_NOCLIP = 2;

    /** Border size, currently hard-coded because of clipping assumptions with outer borders. */
    static final float TABLE_BORDER_SIZE = 1.0f;

    /** Extend resize separators outside inner borders. */
    static final float TABLE_RESIZE_SEPARATOR_HALF_THICKNESS = 4.0f;

    /** Delay in milliseconds before making the resize hover feedback visible. */
    static final long TABLE_RESIZE_SEPARATOR_FEEDBACK_TIMER = 60;

    /** The maximum number of columns. */
    static final int TABLE_MAX_COLUMNS = 512;

    /** A special sentinel color to mark disabled background colors. */
    static final int COLOR_DISABLE = Color.rgba(0, 0, 0, 1);

    static Context context;

    // ---------------------------------------------------------------------------------------------
    // Main code
    // ---------------------------------------------------------------------------------------------

    /**
     * Adjust table flags, setting the default sizing policy and fixing incompatible flags.
     *
     * @param flags The flags passed in.
     * @param outerWindow The window the table is in.
     * @return The fixed flags.
     */
    private static int tableFixFlags(int flags, @NonNull Window outerWindow) {
        // Set the default sizing policy
        if ((flags & TableFlags.INTERNAL_SIZING_MASK) == 0) {
            flags |=
                    ((flags & TableFlags.SCROLL_X) != 0
                                    || (outerWindow.flags & WindowFlags.ALWAYS_AUTO_RESIZE) != 0)
                            ? TableFlags.SIZING_FIXED_FIT
                            : TableFlags.SIZING_STRETCH_SAME;
        }

        // Enable NO_KEEP_COLUMNS_VISIBLE when using SIZING_FIXED_SAME
        if ((flags & TableFlags.INTERNAL_SIZING_MASK) == TableFlags.SIZING_FIXED_SAME) {
            flags |= TableFlags.NO_KEEP_COLUMNS_VISIBLE;
        }

        // Enforce borders when resizable
        if ((flags & TableFlags.RESIZABLE) != 0) {
            flags |= TableFlags.BORDERS_INNER_V;
        }

        // Disable NO_HOST_EXTEND_X/Y if we have any scrolling going on
        if ((flags & (TableFlags.SCROLL_X | TableFlags.SCROLL_Y)) != 0) {
            flags &= ~(TableFlags.NO_HOST_EXTEND_X | TableFlags.NO_HOST_EXTEND_Y);
        }

        // NO_BORDERS_IN_BODY_UNTIL_RESIZE takes priority over NO_BORDERS_IN_BODY
        if ((flags & TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE) != 0) {
            flags &= ~TableFlags.NO_BORDERS_IN_BODY;
        }

        // Disable saved settings if there's nothing to save
        if ((flags
                        & (TableFlags.RESIZABLE
                                | TableFlags.HIDEABLE
                                | TableFlags.REORDERABLE
                                | TableFlags.SORTABLE))
                == 0) {
            flags |= TableFlags.NO_SAVED_SETTINGS;
        }

        // Inherit NO_SAVED_SETTINGS from the top-level window
        if ((outerWindow.rootWindow.flags & WindowFlags.NO_SAVED_SETTINGS) != 0) {
            flags |= TableFlags.NO_SAVED_SETTINGS;
        }

        return flags;
    }

    /**
     * Find a table by ID.
     *
     * @param id The table ID.
     * @return The table, or null if there is none with that ID.
     */
    static Table tableFindByID(int id) {
        return context.tablesByID.get(id);
    }

    /**
     * Begin a table. See the comments at the top of imgui_tables.cpp in Dear ImGui for details on
     * sizing.
     *
     * @param name The table name, which is also used for the ID.
     * @param columnsCount The number of columns, between 1 and 511.
     * @param tableFlags Flags for the table.
     * @param outerWidth The outer width, 0 to fill the available width.
     * @param outerHeight The outer height, 0 for no minimum height (or to bottom-align when
     *     scrolling).
     * @param innerWidth The inner width, only used with TableFlags.SCROLL_X.
     * @return True if the table is visible, only call endTable() if this returns true.
     * @see TableFlags
     */
    public static boolean beginTable(
            @NonNull String name,
            int columnsCount,
            int tableFlags,
            float outerWidth,
            float outerHeight,
            float innerWidth) {
        final int id = IkGuiImplUtils.getID(name);
        return beginTableEx(
                name, id, columnsCount, tableFlags, outerWidth, outerHeight, innerWidth);
    }

    static boolean beginTableEx(
            @NonNull String name,
            int id,
            int columnsCount,
            int flags,
            float outerWidth,
            float outerHeight,
            float innerWidth) {
        final Window outerWindow = IkGuiInternal.getCurrentWindow();
        if (outerWindow.skipItems) {
            return false;
        }

        if (columnsCount <= 0 || columnsCount >= TABLE_MAX_COLUMNS) {
            IkGuiImplDebugTools.reportError(
                    log, "beginTable() invalid column count {}", columnsCount);
            return false;
        }
        if ((flags & TableFlags.SCROLL_X) != 0 && innerWidth < 0.0f) {
            IkGuiImplDebugTools.reportError(
                    log, "beginTable() inner width can't be negative with SCROLL_X");
            innerWidth = 0.0f;
        }

        // If an outer size is specified ahead we will be able to early out when not visible
        final boolean useChildWindow = (flags & (TableFlags.SCROLL_X | TableFlags.SCROLL_Y)) != 0;
        final Vector2f available = IkGuiImplUtils.getContentRegionAvailable();
        final Vector2f actualOuterSize =
                IkGuiInternal.calcItemSize(
                        new Vector2f(outerWidth, outerHeight),
                        Math.max(available.x, IkGuiImplWindows.WINDOW_HARD_MIN_SIZE),
                        useChildWindow
                                ? Math.max(available.y, IkGuiImplWindows.WINDOW_HARD_MIN_SIZE)
                                : 0.0f);
        actualOuterSize.set(
                IkGuiInternal.truncate(actualOuterSize.x),
                IkGuiInternal.truncate(actualOuterSize.y));
        final RectFloat outerRect =
                new RectFloat(
                        outerWindow.cursorPosition.x,
                        outerWindow.cursorPosition.y,
                        outerWindow.cursorPosition.x + actualOuterSize.x,
                        outerWindow.cursorPosition.y + actualOuterSize.y);
        // Doesn't apply to ALWAYS_AUTO_RESIZE windows
        final boolean outerWindowIsMeasuringSize =
                outerWindow.autoFitFramesX.get() > 0 || outerWindow.autoFitFramesY.get() > 0;
        if (useChildWindow
                && IkGuiInternal.isClippedEx(outerRect, 0)
                && !outerWindowIsMeasuringSize) {
            IkGuiInternal.itemSize(outerRect, -1.0f);
            IkGuiInternal.itemAdd(outerRect, id);
            context.nextWindowData.clearFlags();
            return false;
        }

        // Debug break requested by the user
        if (context.debugBreakInTable == id) {
            IkGuiImplDebugTools.debugBreak("beginTable() of table '" + name + "'");
        }

        // Acquire storage for the table
        Table table = context.tablesByID.get(id);
        if (table == null) {
            table = new Table();
            context.tablesByID.put(id, table);
            context.tables.add(table);
        }
        final int tableIndex = context.tables.indexOf(table);

        // Acquire temporary buffers
        if (++context.tablesTempDataStacked > context.tablesTempData.size()) {
            context.tablesTempData.add(new TableTempData());
        }
        final TableTempData tempData =
                context.tablesTempData.get(context.tablesTempDataStacked - 1);
        table.tempData = tempData;
        tempData.tableIndex = tableIndex;
        tempData.reconcileColumnsRequests.clear();
        table.drawSplitter = tempData.drawSplitter;

        // Fix flags
        table.isDefaultSizingPolicy = (flags & TableFlags.INTERNAL_SIZING_MASK) == 0;
        flags = tableFixFlags(flags, outerWindow);

        // Initialize
        final int previousFrameActive = table.lastFrameActive;
        final int instanceNumber =
                previousFrameActive != context.frameCount ? 0 : table.instanceCurrent + 1;
        final int previousFlags = table.flags;
        table.id = id;
        table.flags = flags;
        table.lastFrameActive = context.frameCount;
        table.outerWindow = outerWindow;
        table.innerWindow = outerWindow;
        table.columnsCount = columnsCount;
        table.isLayoutLocked = false;
        table.innerWidth = innerWidth;
        table.navLayer = outerWindow.navLayerCurrent;
        table.isNewTable = previousFrameActive == -1;
        tempData.userOuterSize.set(outerWidth, outerHeight);

        // Instance data (for instance 0, the table ID is the instance ID)
        final int instanceID;
        table.instanceCurrent = instanceNumber;
        if (instanceNumber > 0) {
            if (table.columnsCount != columnsCount) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "beginTable() can't change the column count mid-frame with the same ID");
            }
            if (table.instanceDataExtra.size() < instanceNumber) {
                table.instanceDataExtra.add(new TableInstanceData());
            }
            instanceID = Hash.getID(instanceNumber, Hash.getID("##Instances", id));
        } else {
            instanceID = id;
        }
        final TableInstanceData tableInstance = table.getInstanceData(table.instanceCurrent);
        tableInstance.tableInstanceID = instanceID;

        // When not using a child window, the work rect will grow as we append contents
        if (useChildWindow) {
            // Ensure no vertical scrollbar appears if we only want a horizontal one
            float overrideContentWidth = Float.MAX_VALUE;
            float overrideContentHeight = Float.MAX_VALUE;
            if ((flags & TableFlags.SCROLL_X) != 0 && (flags & TableFlags.SCROLL_Y) == 0) {
                overrideContentHeight = Float.MIN_NORMAL;
            }

            // Ensure the specified width (when not specified, stretched columns will act as if the
            // width is the outer width and never lead to scrolling)
            if ((flags & TableFlags.SCROLL_X) != 0 && innerWidth > 0.0f) {
                overrideContentWidth = innerWidth;
            }

            if (overrideContentWidth != Float.MAX_VALUE
                    || overrideContentHeight != Float.MAX_VALUE) {
                IkGuiImplWindows.setNextWindowContentSize(
                        overrideContentWidth != Float.MAX_VALUE ? overrideContentWidth : 0.0f,
                        overrideContentHeight != Float.MAX_VALUE ? overrideContentHeight : 0.0f);
            }

            // Reset scroll if we are reactivating it
            if ((previousFlags & (TableFlags.SCROLL_X | TableFlags.SCROLL_Y)) == 0
                    && (context.nextWindowData.fieldFlags & NextWindowFlags.HAS_SCROLL) == 0) {
                IkGuiImplWindows.setNextWindowScroll(0.0f, 0.0f);
            }

            // Create the scrolling region (without border and zero window padding)
            final int childWindowFlags =
                    (flags & TableFlags.SCROLL_X) != 0
                            ? WindowFlags.HORIZONTAL_SCROLLBAR
                            : WindowFlags.NONE;
            IkGuiImplWindows.beginChild(
                    name,
                    instanceID,
                    outerRect.getWidth(),
                    outerRect.getHeight(),
                    ChildFlags.NONE,
                    childWindowFlags);
            table.innerWindow = context.windowCurrent;
            table.workRect.set(table.innerWindow.rectWork);
            table.outerRect.set(
                    table.innerWindow.position.x,
                    table.innerWindow.position.y,
                    table.innerWindow.position.x + table.innerWindow.size.x,
                    table.innerWindow.position.y + table.innerWindow.size.y);
            table.innerRect.set(table.innerWindow.rectInner);

            // Allow submitting when the host is measuring
            if (table.innerWindow.skipItems && outerWindowIsMeasuringSize) {
                table.innerWindow.skipItems = false;
            }

            // When using multiple instances, ensure they have the same amount of horizontal
            // decorations (a vertical scrollbar) so stretched columns can be aligned
            if (instanceNumber == 0) {
                table.hasScrollbarYPrevious = table.hasScrollbarYCurrent;
                table.hasScrollbarYCurrent = false;
            }
            table.hasScrollbarYCurrent |= table.innerWindow.scrollbarY;
        } else {
            // For non-scrolling tables, the work rect, outer rect, and inner rect are the same,
            // but we don't have a correct bottom (unless a height has been explicitly passed in).
            // It will only be updated in endTable().
            table.workRect.set(outerRect);
            table.outerRect.set(outerRect);
            table.innerRect.set(outerRect);
            table.hasScrollbarYPrevious = false;
            table.hasScrollbarYCurrent = false;
            // This is designed to always link tree node lines across a table
            table.innerWindow.treeDepth++;
        }

        // Push a standardized ID for both child-using and not-child-using tables
        IkGuiInternal.pushOverrideID(id);
        if (instanceNumber > 0) {
            IkGuiInternal.pushOverrideID(instanceID);
        }

        // Backup a copy of host window members we will modify
        final Window innerWindow = table.innerWindow;
        table.hostIndentX = innerWindow.indent;
        table.hostClipRect.set(innerWindow.rectCurrentClip);
        table.hostSkipItems = innerWindow.skipItems;
        tempData.windowID = innerWindow.id;
        tempData.hostBackupWorkRect.set(innerWindow.rectWork);
        tempData.hostBackupParentWorkRect.set(innerWindow.rectParentWork);
        tempData.hostBackupPrevLineSize.set(innerWindow.lineSizePrevious);
        tempData.hostBackupCurrLineSize.set(innerWindow.lineSizeCurrent);
        tempData.hostBackupCursorMaxPos.set(innerWindow.cursorMaxPosition);
        tempData.hostBackupColumnsOffset = outerWindow.columnsOffset;
        tempData.hostBackupItemWidth = outerWindow.currentItemWidth;
        tempData.hostBackupItemWidthStackSize = outerWindow.itemWidthStack.size();
        innerWindow.lineSizePrevious.set(0.0f, 0.0f);
        innerWindow.lineSizeCurrent.set(0.0f, 0.0f);

        // Make borders not overlap our contents by offsetting the host clip rect
        if (innerWindow != outerWindow) {
            final float borderSize = TABLE_BORDER_SIZE;
            final RectFloat host = table.hostClipRect;
            if ((flags & TableFlags.BORDERS_OUTER_V) != 0) {
                host.setLeft(Math.min(host.getLeft() + borderSize, host.getRight()));
                if (innerWindow.decoOuterSizeX2 == 0.0f) {
                    host.setRight(Math.max(host.getRight() - borderSize, host.getLeft()));
                }
            }
            if ((flags & TableFlags.BORDERS_OUTER_H) != 0) {
                host.setTop(Math.min(host.getTop() + borderSize, host.getBottom()));
                if (innerWindow.decoOuterSizeY2 == 0.0f) {
                    host.setBottom(Math.max(host.getBottom() - borderSize, host.getTop()));
                }
            }
        }

        // Padding and spacing
        // - None               ........Content..... Pad .....Content........
        // - PadOuter           | Pad ..Content..... Pad .....Content.. Pad |
        // - PadInner           ........Content.. Pad | Pad ..Content........
        // - PadOuter+PadInner  | Pad ..Content.. Pad | Pad ..Content.. Pad |
        final StyleVariables style = context.style.variable;
        final boolean padOuterX =
                (flags & TableFlags.NO_PAD_OUTER_X) != 0
                        ? false
                        : (flags & TableFlags.PAD_OUTER_X) != 0
                                || (flags & TableFlags.BORDERS_OUTER_V) != 0;
        final boolean padInnerX = (flags & TableFlags.NO_PAD_INNER_X) == 0;
        final float innerSpacingForBorder =
                (flags & TableFlags.BORDERS_INNER_V) != 0 ? TABLE_BORDER_SIZE : 0.0f;
        final float innerSpacingExplicit =
                padInnerX && (flags & TableFlags.BORDERS_INNER_V) == 0 ? style.cellPadding.x : 0.0f;
        final float innerPaddingExplicit =
                padInnerX && (flags & TableFlags.BORDERS_INNER_V) != 0 ? style.cellPadding.x : 0.0f;
        table.cellSpacingX1 = innerSpacingExplicit + innerSpacingForBorder;
        table.cellSpacingX2 = innerSpacingExplicit;
        table.cellPaddingX = innerPaddingExplicit;

        final float outerPaddingForBorder =
                (flags & TableFlags.BORDERS_OUTER_V) != 0 ? TABLE_BORDER_SIZE : 0.0f;
        final float outerPaddingExplicit = padOuterX ? style.cellPadding.x : 0.0f;
        table.outerPaddingX = outerPaddingForBorder + outerPaddingExplicit - table.cellPaddingX;

        table.currentColumn = -1;
        table.currentRow = -1;
        table.rowBackgroundColorCounter = 0;
        table.lastRowFlags = TableRowFlags.NONE;
        table.innerClipRect.set(
                innerWindow == outerWindow ? table.workRect : innerWindow.rectCurrentClip);
        // We need this to honor the inner width
        table.innerClipRect.clipWith(table.workRect);
        table.innerClipRect.clipWithFull(table.hostClipRect);
        table.innerClipRect.setBottom(
                (flags & TableFlags.NO_HOST_EXTEND_Y) != 0
                        ? Math.min(
                                table.innerClipRect.getBottom(), innerWindow.rectWork.getBottom())
                        : table.hostClipRect.getBottom());

        table.rowPosY1 = table.workRect.getTop();
        table.rowPosY2 = table.workRect.getTop();
        // This will be cleared again by tableBeginRow()
        table.rowTextBaseline = 0.0f;
        table.rowCellPaddingY = 0.0f;
        // These will be set up by tableSetupScrollFreeze(), if any
        table.freezeRowsRequest = 0;
        table.freezeRowsCount = 0;
        table.freezeColumnsRequest = 0;
        table.freezeColumnsCount = 0;
        table.isUnfrozenRows = true;
        table.declColumnsCount = 0;
        table.angledHeadersCount = 0;
        if (previousFrameActive + 1 < context.frameCount) {
            table.isActiveIDInTable = false;
        }
        table.angledHeadersHeight = 0.0f;
        tempData.angledHeadersExtraWidth = 0.0f;

        // Using opaque colors facilitates overlapping lines of the grid
        table.borderColorStrong =
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TABLE_BORDER_STRONG);
        table.borderColorLight =
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TABLE_BORDER_LIGHT);

        // Make the table current
        context.currentTable = table;
        // Shortcut for updating whether the window is scroll pushable on X
        innerWindow.navIsScrollPushableX = false;
        outerWindow.currentTableIndex = tableIndex;
        if (innerWindow != outerWindow) {
            // So endChild() within the inner window can restore the table properly
            innerWindow.currentTableIndex = tableIndex;
        }

        if ((previousFlags & TableFlags.REORDERABLE) != 0
                && (flags & TableFlags.REORDERABLE) == 0) {
            table.isResetDisplayOrderRequest = true;
        }

        // Mark as used
        tempData.lastTimeActive = context.time;

        // Set up the memory buffer (clear data if the column count changed)
        final int oldColumnsCount = table.columns.length;
        if (oldColumnsCount != 0 && oldColumnsCount != columnsCount) {
            // Attempt to preserve widths and other settings on column count changes
            tempData.oldColumnsData = table.columns;
            for (TableColumn sourceColumn : tempData.oldColumnsData) {
                sourceColumn.isNeedReconcileSrc = true;
            }
            table.columns = new TableColumn[0];
        }
        if (table.columns.length == 0) {
            tableBeginInitMemory(table, columnsCount);
            table.isInitializing = true;
        }
        if (table.isResetAllRequest) {
            IkGuiImplTableSettings.tableResetSettings(table);
        }
        if (table.isInitializing) {
            // Initialize
            if (table.isNewTable) {
                table.settings = null;
                table.isSettingsRequestLoad = true;
            }
            table.isSortSpecsDirty = true;
            // Records itself into the .ini file even when in the default state
            table.isSettingsDirty = true;
            table.isReconcileMode = false;
            table.instanceInteracted = -1;
            table.contextPopupColumn = -1;
            table.reorderColumn = -1;
            table.reorderColumnDstOrder = -1;
            table.resizedColumn = -1;
            table.lastResizedColumn = -1;
            table.autoFitSingleColumn = -1;
            table.hoveredColumnBody = -1;
            table.hoveredColumnBorder = -1;
            for (int n = 0; n < columnsCount; ++n) {
                final TableColumn column = table.columns[n];
                if (tempData.oldColumnsData != null && n < tempData.oldColumnsData.length) {
                    column.set(tempData.oldColumnsData[n]);
                } else {
                    final float widthAuto = column.widthAuto;
                    column.reset();
                    column.widthAuto = widthAuto;
                    // Preserve the auto width when reinitializing a live table, which removes a
                    // visible flicker
                    column.isPreserveWidthAuto = true;
                    column.isEnabled = true;
                    column.isUserEnabled = true;
                    column.isUserEnabledNextFrame = true;
                    column.displayOrder = n;
                }
                table.displayOrderToIndex[n] = column.displayOrder;
            }
        }

        // Load settings
        if (table.isSettingsRequestLoad) {
            IkGuiImplTableSettings.tableLoadSettings(table);
        }

        // Disable output until the user calls tableNextRow() or tableNextColumn(), which lead to
        // the tableUpdateLayout() call. This reduces cases where "out of table" output would be
        // misleading to the user.
        innerWindow.skipItems = true;

        return true;
    }

    /**
     * Allocate the per-column arrays for a table.
     *
     * @param table The table.
     * @param columnsCount The number of columns.
     */
    static void tableBeginInitMemory(@NonNull Table table, int columnsCount) {
        table.columns = new TableColumn[columnsCount];
        table.displayOrderToIndex = new int[columnsCount];
        table.rowCellData = new TableCellData[columnsCount];
        for (int n = 0; n < columnsCount; ++n) {
            table.columns[n] = new TableColumn();
            table.rowCellData[n] = new TableCellData();
        }
        table.enabledMaskByDisplayOrder.clear();
        table.enabledMaskByIndex.clear();
        table.visibleMaskByIndex.clear();
    }

    /**
     * Apply queued resizing/reordering/hiding requests.
     *
     * @param table The table.
     */
    static void tableApplyQueuedRequests(@NonNull Table table) {
        // Handle resizing requests, in the beginTable() of the first instance of each table
        if (table.instanceCurrent == 0) {
            if (table.resizedColumn != -1 && table.resizedColumnNextWidth != Float.MAX_VALUE) {
                tableSetColumnWidth(table.resizedColumn, table.resizedColumnNextWidth);
            }
            table.lastResizedColumn = table.resizedColumn;
            table.resizedColumnNextWidth = Float.MAX_VALUE;
            table.resizedColumn = -1;

            // Process auto-fit for a single column, which is a special case for stretch columns and
            // fixed columns with the SIZING_FIXED_SAME policy
            if (table.autoFitSingleColumn != -1) {
                tableSetColumnWidth(
                        table.autoFitSingleColumn,
                        table.columns[table.autoFitSingleColumn].widthAuto);
                table.autoFitSingleColumn = -1;
            }
        }

        // Handle reordering requests
        if (table.instanceCurrent == 0) {
            table.lastHeldHeaderColumn = table.heldHeaderColumn;
            table.heldHeaderColumn = -1;
            if (table.reorderColumn != -1 && table.reorderColumnDstOrder != -1) {
                tableSetColumnDisplayOrder(table, table.reorderColumn, table.reorderColumnDstOrder);
                table.reorderColumnDstOrder = -1;
            }

            // Release
            if (context.activeID == 0) {
                table.reorderColumn = -1;
            }
        }

        // Handle display order / visibility reset requests
        if (table.isResetDisplayOrderRequest) {
            for (int n = 0; n < table.columnsCount; ++n) {
                table.displayOrderToIndex[n] = n;
                table.columns[n].displayOrder = n;
            }
            table.isResetDisplayOrderRequest = false;
            table.isSettingsDirty = true;
        }
        if (table.isResetVisibilityRequest) {
            for (TableColumn column : table.columns) {
                final boolean enabled = (column.flags & TableColumnFlags.DEFAULT_HIDE) == 0;
                column.isUserEnabled = enabled;
                column.isUserEnabledNextFrame = enabled;
            }
            table.isResetVisibilityRequest = false;
            table.isSettingsDirty = true;
        }
    }

    /**
     * Change the display order of a column immediately. See tableQueueSetColumnDisplayOrder() for
     * additional checks/constraints.
     *
     * @param table The table.
     * @param columnIndex The column index.
     * @param dstOrder The new display order.
     */
    static void tableSetColumnDisplayOrder(@NonNull Table table, int columnIndex, int dstOrder) {
        if (columnIndex < 0 || columnIndex >= table.columnsCount) {
            IkGuiImplDebugTools.reportError(log, "Invalid table column index {}", columnIndex);
            return;
        }
        if (dstOrder < 0 || dstOrder >= table.columnsCount) {
            IkGuiImplDebugTools.reportError(log, "Invalid table column display order {}", dstOrder);
            return;
        }

        final TableColumn sourceColumn = table.columns[columnIndex];
        final int sourceOrder = sourceColumn.displayOrder;
        if (sourceOrder == dstOrder) {
            return;
        }
        final int reorderDirection = dstOrder < sourceOrder ? -1 : 1;

        sourceColumn.displayOrder = dstOrder;
        for (int order = sourceOrder + reorderDirection;
                order != dstOrder + reorderDirection;
                order += reorderDirection) {
            table.columns[table.displayOrderToIndex[order]].displayOrder -= reorderDirection;
        }

        // Rebuild the display order from the columns
        for (int n = 0; n < table.columnsCount; ++n) {
            table.displayOrderToIndex[table.columns[n].displayOrder] = n;
        }
        table.isSettingsDirty = true;
    }

    /**
     * Find the closest display order that a column is allowed to move to.
     *
     * @param table The table.
     * @param sourceOrder The current display order of the column.
     * @param dstOrder The requested display order.
     * @return The allowed display order.
     */
    static int tableGetMaxDisplayOrderAllowed(@NonNull Table table, int sourceOrder, int dstOrder) {
        dstOrder = MathUtil.clamp(dstOrder, 0, table.columnsCount - 1);
        if (sourceOrder == dstOrder) {
            return dstOrder;
        }

        // Can't cross over the frozen column limit when interactively reordering
        if (table.freezeColumnsRequest > 0) {
            dstOrder =
                    sourceOrder < table.freezeColumnsRequest
                            ? Math.min(dstOrder, table.freezeColumnsRequest - 1)
                            : Math.max(dstOrder, table.freezeColumnsRequest);
        }

        // Can't cross over a column with the NO_REORDER flag
        final int reorderDirection = sourceOrder < dstOrder ? 1 : -1;
        for (int order = sourceOrder;
                (sourceOrder < dstOrder && order <= dstOrder)
                        || (dstOrder < sourceOrder && order >= dstOrder);
                order += reorderDirection) {
            if ((table.columns[table.displayOrderToIndex[order]].flags
                            & TableColumnFlags.NO_REORDER)
                    != 0) {
                dstOrder = order == sourceOrder ? sourceOrder : order - reorderDirection;
                break;
            }
        }
        return dstOrder;
    }

    /**
     * Reorder a column, as requested by user interaction.
     *
     * @param table The table.
     * @param columnIndex The column index.
     * @param dstOrder The requested display order.
     */
    static void tableQueueSetColumnDisplayOrder(
            @NonNull Table table, int columnIndex, int dstOrder) {
        final int sourceOrder = table.columns[columnIndex].displayOrder;
        table.reorderColumn = columnIndex;
        table.reorderColumnDstOrder = -1;
        dstOrder = tableGetMaxDisplayOrderAllowed(table, sourceOrder, dstOrder);
        // We allow calling this before layout with reconcile, so don't early out
        if (table.isLayoutLocked && dstOrder == sourceOrder) {
            return;
        }
        table.reorderColumnDstOrder = dstOrder;
    }

    /**
     * Adjust column flags: default width mode, and stretch columns are not allowed when auto
     * extending.
     *
     * @param table The table.
     * @param column The column.
     * @param flagsIn The flags passed in.
     */
    private static void tableSetupColumnFlags(
            @NonNull Table table, @NonNull TableColumn column, int flagsIn) {
        int flags = flagsIn;

        // Sizing policy
        if ((flags & TableColumnFlags.INTERNAL_WIDTH_MASK) == 0) {
            final int tableSizingPolicy = table.flags & TableFlags.INTERNAL_SIZING_MASK;
            if (tableSizingPolicy == TableFlags.SIZING_FIXED_FIT
                    || tableSizingPolicy == TableFlags.SIZING_FIXED_SAME) {
                flags |= TableColumnFlags.WIDTH_FIXED;
            } else {
                flags |= TableColumnFlags.WIDTH_STRETCH;
            }
        } else if (Integer.bitCount(flags & TableColumnFlags.INTERNAL_WIDTH_MASK) != 1) {
            IkGuiImplDebugTools.reportError(log, "Only one column width flag may be used");
        }

        // Resize
        if ((table.flags & TableFlags.RESIZABLE) == 0) {
            flags |= TableColumnFlags.NO_RESIZE;
        }

        // Sorting
        if ((flags & TableColumnFlags.NO_SORT_ASCENDING) != 0
                && (flags & TableColumnFlags.NO_SORT_DESCENDING) != 0) {
            flags |= TableColumnFlags.NO_SORT;
        }

        // Indentation
        if ((flags & TableColumnFlags.INTERNAL_INDENT_MASK) == 0) {
            flags |=
                    table.columns[0] == column
                            ? TableColumnFlags.INDENT_ENABLE
                            : TableColumnFlags.INDENT_DISABLE;
        }

        // Preserve status flags
        column.flags = flags | (column.flags & TableColumnFlags.INTERNAL_STATUS_MASK);

        // Build an ordered list of available sort directions
        column.sortDirectionsAvailableCount = 0;
        column.sortDirectionsAvailableMask = 0;
        column.sortDirectionsAvailableList = 0;
        if ((table.flags & TableFlags.SORTABLE) != 0) {
            int count = 0;
            int mask = 0;
            int list = 0;
            final int ascending = SortDirection.ASCENDING.ordinal();
            final int descending = SortDirection.DESCENDING.ordinal();
            final boolean preferAscending = (flags & TableColumnFlags.PREFER_SORT_ASCENDING) != 0;
            final boolean preferDescending = (flags & TableColumnFlags.PREFER_SORT_DESCENDING) != 0;
            final boolean noAscending = (flags & TableColumnFlags.NO_SORT_ASCENDING) != 0;
            final boolean noDescending = (flags & TableColumnFlags.NO_SORT_DESCENDING) != 0;
            if (preferAscending && !noAscending) {
                mask |= 1 << ascending;
                list |= ascending << (count << 1);
                count++;
            }
            if (preferDescending && !noDescending) {
                mask |= 1 << descending;
                list |= descending << (count << 1);
                count++;
            }
            if (!preferAscending && !noAscending) {
                mask |= 1 << ascending;
                list |= ascending << (count << 1);
                count++;
            }
            if (!preferDescending && !noDescending) {
                mask |= 1 << descending;
                list |= descending << (count << 1);
                count++;
            }
            if ((table.flags & TableFlags.SORT_TRISTATE) != 0 || count == 0) {
                mask |= 1 << SortDirection.NONE.ordinal();
                count++;
            }
            column.sortDirectionsAvailableList = list;
            column.sortDirectionsAvailableMask = mask;
            column.sortDirectionsAvailableCount = count;
            tableFixColumnSortDirection(table, column);
        }
    }

    /**
     * Lay out columns for the frame. This is the follow-up to beginTable(), and the largest
     * function. It runs on the first call to tableNextRow(), to give a chance for
     * tableSetupColumn() and other setup functions to be called first.
     *
     * @param table The table.
     */
    static void tableUpdateLayout(@NonNull Table table) {
        final TableTempData tempData = table.tempData;
        if (table.isLayoutLocked) {
            IkGuiImplDebugTools.reportError(log, "Table layout is already locked");
            return;
        }
        final int columnsCount = table.columnsCount;
        final StyleVariables style = context.style.variable;

        // Reconcile moved columns
        if (!tempData.reconcileColumnsRequests.isEmpty()) {
            tableReconcileColumns(table);
        }
        tempData.oldColumnsData = null;

        // Apply column settings
        if (table.isSettingsRequestLoad) {
            IkGuiImplTableSettings.tableLoadSettingsForColumns(table);
        }
        if (table.isInitializing || table.isSettingsRequestLoad) {
            for (TableColumn column : table.columns) {
                final int initFlags;
                if (table.isSettingsRequestLoad) {
                    initFlags = column.isLoadedSettings ? ~table.settingsLoadedFlags : ~0;
                } else {
                    initFlags = column.isJustCreated ? ~0 : 0;
                }
                tableInitColumnDefaults(table, column, initFlags);
            }
            // Call even for non-reorderable tables as we loaded .ini data
            tableFixDisplayOrder(table);
            table.isSettingsRequestLoad = false;
        }

        // Apply queued resizing/reordering/hiding requests
        tableApplyQueuedRequests(table);

        // Handle DPI/font resizing, which facilitates DPI changes with the assumption that the cell
        // padding has been scaled as well.
        final float newRefScaleUnit = IkGuiInternal.getFontSize();
        if (table.refScale != 0.0f && table.refScale != newRefScaleUnit) {
            final float scaleFactor = newRefScaleUnit / table.refScale;
            for (int n = 0; n < columnsCount; ++n) {
                table.columns[n].widthRequest = table.columns[n].widthRequest * scaleFactor;
            }
        }
        table.refScale = newRefScaleUnit;

        final int tableSizingPolicy = table.flags & TableFlags.INTERNAL_SIZING_MASK;
        table.isDefaultDisplayOrder = true;
        table.isDefaultVisibility = true;
        table.columnsEnabledCount = 0;
        table.enabledMaskByIndex.clear();
        table.enabledMaskByDisplayOrder.clear();
        table.leftMostEnabledColumn = -1;
        table.minColumnWidth = Math.max(1.0f, style.framePadding.x * 1.0f);

        // [Part 1] Apply/lock enabled and order states. Calculate auto/ideal widths for columns.
        // Count fixed/stretch columns. Process columns in their visible orders as we are building
        // the previous/next indices.
        int countFixed = 0;
        int countStretch = 0;
        int prevVisibleColumnIndex = -1;
        boolean hasAutoFitRequest = false;
        boolean hasResizable = false;
        float stretchSumWidthAuto = 0.0f;
        float fixedMaxWidthAuto = 0.0f;
        for (int order = 0; order < columnsCount; ++order) {
            final int columnIndex = table.displayOrderToIndex[order];
            final TableColumn column = table.columns[columnIndex];

            // Clear column setup if not submitted by the user. It is currently mandatory to call
            // tableSetupColumn() every frame.
            if (table.declColumnsCount <= columnIndex) {
                tableSetupColumnFlags(table, column, TableColumnFlags.NONE);
                column.name = null;
                column.id = 0;
                column.userData = 0;
                column.initStretchWeightOrWidth = -1.0f;
            }

            // Update the enabled state, mark settings and sort specs dirty
            if ((table.flags & TableFlags.HIDEABLE) == 0
                    || (column.flags & TableColumnFlags.NO_HIDE) != 0) {
                column.isUserEnabledNextFrame = true;
            }
            if (column.isUserEnabled != column.isUserEnabledNextFrame) {
                column.isUserEnabled = column.isUserEnabledNextFrame;
                table.isSettingsDirty = true;
            }
            column.isEnabled =
                    column.isUserEnabled && (column.flags & TableColumnFlags.DISABLED) == 0;
            column.isJustCreated = false;

            if (column.isEnabled != ((column.flags & TableColumnFlags.DEFAULT_HIDE) == 0)) {
                table.isDefaultVisibility = false;
            }
            if (columnIndex != order) {
                table.isDefaultDisplayOrder = false;
            }

            if (column.sortOrder != -1 && !column.isEnabled) {
                table.isSortSpecsDirty = true;
            }
            if (column.sortOrder > 0 && (table.flags & TableFlags.SORT_MULTI) == 0) {
                table.isSortSpecsDirty = true;
            }

            // Auto-fit unsized columns
            final boolean startAutoFit =
                    (column.flags & TableColumnFlags.WIDTH_FIXED) != 0
                            ? column.widthRequest < 0.0f
                            : column.stretchWeight < 0.0f;
            if (startAutoFit) {
                // Fit for three frames
                column.autoFitQueue = (1 << 3) - 1;
                column.cannotSkipItemsQueue = (1 << 3) - 1;
            }

            if (!column.isEnabled) {
                column.indexWithinEnabledSet = -1;
                continue;
            }

            // Mark as enabled and link to the previous/next enabled column
            column.prevEnabledColumn = prevVisibleColumnIndex;
            column.nextEnabledColumn = -1;
            if (prevVisibleColumnIndex != -1) {
                table.columns[prevVisibleColumnIndex].nextEnabledColumn = columnIndex;
            } else {
                table.leftMostEnabledColumn = columnIndex;
            }
            column.indexWithinEnabledSet = table.columnsEnabledCount++;
            table.enabledMaskByIndex.set(columnIndex);
            table.enabledMaskByDisplayOrder.set(column.displayOrder);
            prevVisibleColumnIndex = columnIndex;

            // Calculate the ideal/auto column width (the width required for all contents to be
            // visible without clipping). Combine widths from regular rows and headers unless
            // requested not to.
            if (!column.isPreserveWidthAuto && table.instanceCurrent == 0) {
                column.widthAuto = tableGetColumnWidthAuto(table, column);
            }

            // Non-resizable columns keep their requested width (apply the user value regardless
            // of isPreserveWidthAuto)
            final boolean columnIsResizable = (column.flags & TableColumnFlags.NO_RESIZE) == 0;
            if (columnIsResizable) {
                hasResizable = true;
            }
            if ((column.flags & TableColumnFlags.WIDTH_FIXED) != 0
                    && column.initStretchWeightOrWidth > 0.0f
                    && !columnIsResizable) {
                column.widthAuto = column.initStretchWeightOrWidth;
            }

            if (column.autoFitQueue != 0) {
                hasAutoFitRequest = true;
            }
            if ((column.flags & TableColumnFlags.WIDTH_STRETCH) != 0) {
                stretchSumWidthAuto += column.widthAuto;
                countStretch++;
            } else {
                fixedMaxWidthAuto = Math.max(fixedMaxWidthAuto, column.widthAuto);
                countFixed++;
            }
        }
        if ((table.flags & TableFlags.SORTABLE) != 0
                && table.sortSpecsCount == 0
                && (table.flags & TableFlags.SORT_TRISTATE) == 0) {
            table.isSortSpecsDirty = true;
        }
        table.rightMostEnabledColumn = prevVisibleColumnIndex;
        if (table.leftMostEnabledColumn < 0 || table.rightMostEnabledColumn < 0) {
            IkGuiImplDebugTools.reportError(log, "Tables require at least one enabled column");
        }

        // [Part 2] Disable child window clipping while fitting columns. This makes it possible to
        // avoid the column fitting having to wait until the first visible frame of the child
        // container.
        if (hasAutoFitRequest && table.outerWindow != table.innerWindow) {
            table.innerWindow.skipItems = false;
        }
        if (hasAutoFitRequest) {
            table.isSettingsDirty = true;
        }

        // [Part 3] Fix column flags and record a few extra pieces of information
        // Sum of all widths for fixed and auto-resize columns, excluding widths contributed by
        // stretch columns, but including spacing/padding
        float sumWidthRequests = 0.0f;
        // Sum of all weights for stretch columns
        float stretchSumWeights = 0.0f;
        table.leftMostStretchedColumn = -1;
        table.rightMostStretchedColumn = -1;
        for (int columnIndex = 0; columnIndex < columnsCount; ++columnIndex) {
            if (!table.enabledMaskByIndex.get(columnIndex)) {
                continue;
            }
            final TableColumn column = table.columns[columnIndex];

            final boolean columnIsResizable = (column.flags & TableColumnFlags.NO_RESIZE) == 0;
            if ((column.flags & TableColumnFlags.WIDTH_FIXED) != 0) {
                // Apply the same widths policy
                float widthAuto = column.widthAuto;
                if (tableSizingPolicy == TableFlags.SIZING_FIXED_SAME
                        && (column.autoFitQueue != 0 || !columnIsResizable)) {
                    widthAuto = fixedMaxWidthAuto;
                }

                // Apply the automatic width. Latch the initial size for fixed columns and update it
                // constantly for auto-resizing columns (unless clipped).
                if (column.autoFitQueue != 0) {
                    column.widthRequest = widthAuto;
                } else if (!columnIsResizable && column.isRequestOutput) {
                    column.widthRequest = widthAuto;
                }

                // Increase the minimum size during the init frame to avoid biasing auto-fitting
                // widgets (e.g. textWrapped()) too much
                if (column.autoFitQueue > 1
                        && table.isInitializing
                        && !column.isPreserveWidthAuto) {
                    column.widthRequest =
                            Math.max(column.widthRequest, table.minColumnWidth * 4.0f);
                }
                sumWidthRequests += column.widthRequest;
            } else {
                // Initialize the stretch weight
                if (column.autoFitQueue != 0 || column.stretchWeight < 0.0f || !columnIsResizable) {
                    if (column.initStretchWeightOrWidth > 0.0f) {
                        column.stretchWeight = column.initStretchWeightOrWidth;
                    } else if (tableSizingPolicy == TableFlags.SIZING_STRETCH_PROP) {
                        column.stretchWeight =
                                (column.widthAuto / stretchSumWidthAuto) * countStretch;
                    } else {
                        column.stretchWeight = 1.0f;
                    }
                }

                stretchSumWeights += column.stretchWeight;
                if (table.leftMostStretchedColumn == -1
                        || table.columns[table.leftMostStretchedColumn].displayOrder
                                > column.displayOrder) {
                    table.leftMostStretchedColumn = columnIndex;
                }
                if (table.rightMostStretchedColumn == -1
                        || table.columns[table.rightMostStretchedColumn].displayOrder
                                < column.displayOrder) {
                    table.rightMostStretchedColumn = columnIndex;
                }
            }
            column.isPreserveWidthAuto = false;
            sumWidthRequests += table.cellPaddingX * 2.0f;
        }
        table.columnsEnabledFixedCount = countFixed;
        table.columnsStretchSumWeights = stretchSumWeights;

        // [Part 4] Apply final widths based on requested widths
        final RectFloat workRect = new RectFloat(table.workRect);
        final float widthSpacings =
                table.outerPaddingX * 2.0f
                        + (table.cellSpacingX1 + table.cellSpacingX2)
                                * (table.columnsEnabledCount - 1);
        // To synchronize the decoration width of synced tables with mismatching scrollbar states
        final float widthRemoved =
                table.hasScrollbarYPrevious && !table.innerWindow.scrollbarY
                        ? style.scrollbarSize
                        : 0.0f;
        final float widthAvailable =
                Math.max(
                        1.0f,
                        ((table.flags & TableFlags.SCROLL_X) != 0 && table.innerWidth == 0.0f
                                        ? table.innerClipRect.getWidth()
                                        : workRect.getWidth())
                                - widthRemoved);
        final float widthAvailableForStretchedColumns =
                widthAvailable - widthSpacings - sumWidthRequests;
        float widthRemainingForStretchedColumns = widthAvailableForStretchedColumns;
        table.columnsGivenWidth =
                widthSpacings + (table.cellPaddingX * 2.0f) * table.columnsEnabledCount;
        for (int columnIndex = 0; columnIndex < columnsCount; ++columnIndex) {
            if (!table.enabledMaskByIndex.get(columnIndex)) {
                continue;
            }
            final TableColumn column = table.columns[columnIndex];

            // Allocate width for stretched/weighted columns (the stretch weight gets converted into
            // the width request)
            if ((column.flags & TableColumnFlags.WIDTH_STRETCH) != 0) {
                final float weightRatio = column.stretchWeight / stretchSumWeights;
                column.widthRequest =
                        IkGuiInternal.truncate(
                                Math.max(
                                                widthAvailableForStretchedColumns * weightRatio,
                                                table.minColumnWidth)
                                        + 0.01f);
                widthRemainingForStretchedColumns -= column.widthRequest;
            }

            // [Resize Rule 1] The right-most visible column is not resizable if there is at least
            // one stretch column. See additional comments in tableSetColumnWidth().
            if (column.nextEnabledColumn == -1 && table.leftMostStretchedColumn != -1) {
                column.flags |= TableColumnFlags.INTERNAL_NO_DIRECT_RESIZE;
            }

            // Assign the final width, record the width in case we will need to shrink
            column.widthGiven =
                    IkGuiInternal.truncate(Math.max(column.widthRequest, table.minColumnWidth));
            table.columnsGivenWidth += column.widthGiven;
        }

        // [Part 5] Redistribute the stretch remainder width due to rounding (the remainder width is
        // less than 1 times the number of stretch columns). Using right-to-left distribution, which
        // is more likely to match the resizing cursor.
        if (widthRemainingForStretchedColumns >= 1.0f
                && (table.flags & TableFlags.PRECISE_WIDTHS) == 0) {
            for (int order = columnsCount - 1;
                    stretchSumWeights > 0.0f
                            && widthRemainingForStretchedColumns >= 1.0f
                            && order >= 0;
                    --order) {
                if (!table.enabledMaskByDisplayOrder.get(order)) {
                    continue;
                }
                final TableColumn column = table.columns[table.displayOrderToIndex[order]];
                if ((column.flags & TableColumnFlags.WIDTH_STRETCH) == 0) {
                    continue;
                }
                column.widthRequest += 1.0f;
                column.widthGiven += 1.0f;
                widthRemainingForStretchedColumns -= 1.0f;
            }
        }

        // Determine if the table is hovered, which will be used to flag columns as hovered. We
        // temporarily clear the active ID, which is equivalent to
        // ALLOW_WHEN_BLOCKED_BY_ACTIVE_ITEM.
        // This allows columns to be marked as hovered when e.g. clicking a button inside the
        // column,
        // or using drag and drop.
        final TableInstanceData tableInstance = table.getInstanceData(table.instanceCurrent);
        tableInstance.hoveredRowLast = tableInstance.hoveredRowNext;
        tableInstance.hoveredRowNext = -1;
        table.hoveredColumnBody = -1;
        table.hoveredColumnBorder = -1;
        final RectFloat mouseHitRect =
                new RectFloat(
                        table.outerRect.getLeft(),
                        table.outerRect.getTop(),
                        table.outerRect.getRight(),
                        Math.max(
                                table.outerRect.getBottom(),
                                table.outerRect.getTop() + tableInstance.lastOuterHeight));
        final int backupActiveID = context.activeID;
        context.activeID = 0;
        final boolean isHoveringTable =
                IkGuiInternal.itemHoverable(mouseHitRect, 0, ItemFlags.NONE);
        context.activeID = backupActiveID;

        // Determine the skewed mouse x to support angled headers
        float mouseSkewedX = context.io.mousePosition.x;
        if (table.angledHeadersHeight > 0.0f) {
            final float mouseY = context.io.mousePosition.y;
            if (mouseY >= table.outerRect.getTop()
                    && mouseY <= table.outerRect.getTop() + table.angledHeadersHeight) {
                mouseSkewedX +=
                        IkGuiInternal.truncate(
                                (table.outerRect.getTop() + table.angledHeadersHeight - mouseY)
                                        * table.angledHeadersSlope);
            }
        }

        // [Part 6] Set up final positions, offsets, skip/clip states, and clipping rectangles, and
        // detect the hovered column. Process columns in their visible orders as we are comparing
        // the visible order and adjusting the host clip rect while looping.
        boolean hasAtLeastOneColumnRequestingOutput = false;
        boolean offsetXFrozen = table.freezeColumnsCount > 0;
        float offsetX =
                (table.freezeColumnsCount > 0 ? table.outerRect.getLeft() : workRect.getLeft())
                        + table.outerPaddingX
                        - table.cellSpacingX1;
        final RectFloat hostClipRect = new RectFloat(table.innerClipRect);
        table.visibleMaskByIndex.clear();
        for (int order = 0; order < columnsCount; ++order) {
            final int columnIndex = table.displayOrderToIndex[order];
            final TableColumn column = table.columns[columnIndex];

            // Initial nav layer: using the actual freeze count, not the request, so the header line
            // changes layer when frozen
            column.navLayerCurrent =
                    table.freezeRowsCount > 0 ? IkGuiImplNav.NAV_LAYER_MENU : table.navLayer;

            if (offsetXFrozen && table.freezeColumnsCount == order) {
                offsetX += workRect.getLeft() - table.outerRect.getLeft();
                offsetXFrozen = false;
            }

            // Clear status flags
            column.flags &= ~TableColumnFlags.INTERNAL_STATUS_MASK;

            if (!table.enabledMaskByDisplayOrder.get(order)) {
                // Hidden column: clear a few fields and we are done with it. We set a zero-width
                // clip rect with a proper top/bottom to not interfere with the clipper.
                column.minX = offsetX;
                column.maxX = offsetX;
                column.workMinX = offsetX;
                column.clipRect.set(offsetX, workRect.getTop(), offsetX, Float.MAX_VALUE);
                column.widthGiven = 0.0f;
                column.clipRect.clipWithFull(hostClipRect);
                column.isVisibleX = false;
                column.isVisibleY = false;
                column.isRequestOutput = false;
                column.isSkipItems = true;
                column.itemWidth = 1.0f;
                continue;
            }

            // Lock the start position
            column.minX = offsetX;

            // Lock the width based on the start position and the minimum/maximum width for this
            // position
            column.widthMax = tableCalcMaxColumnWidth(table, columnIndex);
            column.widthGiven = Math.min(column.widthGiven, column.widthMax);
            column.widthGiven =
                    Math.max(
                            column.widthGiven, Math.min(column.widthRequest, table.minColumnWidth));
            column.maxX =
                    offsetX
                            + column.widthGiven
                            + table.cellSpacingX1
                            + table.cellSpacingX2
                            + table.cellPaddingX * 2.0f;

            // Lock other positions. Using maxX for the clip rect makes it easier for the header to
            // receive hover highlight with no discontinuity and display the sorting arrow.
            final float previousInstanceWorkMinX = column.workMinX;
            column.workMinX = column.minX + table.cellPaddingX + table.cellSpacingX1;
            // Expected max
            column.workMaxX = column.maxX - table.cellPaddingX - table.cellSpacingX2;
            column.itemWidth = IkGuiInternal.truncate(column.widthGiven * 0.65f);
            column.clipRect.set(column.minX, workRect.getTop(), column.maxX, Float.MAX_VALUE);
            column.clipRect.clipWithFull(hostClipRect);

            // Mark the column as clipped (not in sight). Scrolling tables (where the inner window
            // differs from the outer window) handle y clipping earlier in beginTable(), so
            // isVisibleY really only applies to non-scrolling tables. Y clipping is disabled
            // because not submitting would reduce the contents width fed to the outer window.
            column.isVisibleX = column.clipRect.getRight() > column.clipRect.getLeft();
            column.isVisibleY = true;
            final boolean isVisible = column.isVisibleX;
            if (isVisible) {
                table.visibleMaskByIndex.set(columnIndex);
            }

            // Mark the column as requesting output from the user. Fixed and non-resizable sets are
            // auto-fitting at all times and therefore always request output.
            column.isRequestOutput =
                    isVisible || column.autoFitQueue != 0 || column.cannotSkipItemsQueue != 0;

            // Mark the column as skipping items (ignoring all items/layout). hostSkipItems is a
            // copy of the inner window skipItems before we cleared it above in part 2.
            column.isSkipItems = !column.isEnabled || table.hostSkipItems;
            if (column.isRequestOutput && !column.isSkipItems) {
                hasAtLeastOneColumnRequestingOutput = true;
            }

            // Update status flags
            column.flags |= TableColumnFlags.IS_ENABLED;
            if (isVisible) {
                column.flags |= TableColumnFlags.IS_VISIBLE;
            }
            if (column.sortOrder != -1) {
                column.flags |= TableColumnFlags.IS_SORTED;
            }

            // Detect the hovered column
            if (isHoveringTable
                    && mouseSkewedX >= column.clipRect.getLeft()
                    && mouseSkewedX < column.clipRect.getRight()) {
                column.flags |= TableColumnFlags.IS_HOVERED;
                table.hoveredColumnBody = columnIndex;
            }

            // Reset content width variables
            if (table.instanceCurrent == 0) {
                column.contentMaxXFrozen = column.workMinX;
                column.contentMaxXUnfrozen = column.workMinX;
                column.contentMaxXHeadersUsed = column.workMinX;
                column.contentMaxXHeadersIdeal = column.workMinX;
            } else {
                // As we store an absolute value to make per-cell updates faster, we need to offset
                // the values used for width computation
                final float offsetFromPreviousInstance = column.workMinX - previousInstanceWorkMinX;
                column.contentMaxXFrozen += offsetFromPreviousInstance;
                column.contentMaxXUnfrozen += offsetFromPreviousInstance;
                column.contentMaxXHeadersUsed += offsetFromPreviousInstance;
                column.contentMaxXHeadersIdeal += offsetFromPreviousInstance;
            }

            // Don't decrement auto-fit counters until the container window got a chance to submit
            // its items
            if (!table.hostSkipItems && table.instanceCurrent == 0) {
                column.autoFitQueue >>= 1;
                column.cannotSkipItemsQueue >>= 1;
            }

            if (order < table.freezeColumnsCount) {
                hostClipRect.setLeft(
                        MathUtil.clamp(
                                column.maxX + TABLE_BORDER_SIZE,
                                hostClipRect.getLeft(),
                                Math.max(hostClipRect.getLeft(), hostClipRect.getRight())));
            }

            offsetX +=
                    column.widthGiven
                            + table.cellSpacingX1
                            + table.cellSpacingX2
                            + table.cellPaddingX * 2.0f;
        }

        // In case the table is visible (e.g. decorations) but all columns are clipped, we keep a
        // column visible. Otherwise we give no chance to a clipper-savvy user to submit rows, and
        // therefore the total contents height used by the scrollbar.
        if (!hasAtLeastOneColumnRequestingOutput && table.leftMostEnabledColumn >= 0) {
            table.columns[table.leftMostEnabledColumn].isRequestOutput = true;
            table.columns[table.leftMostEnabledColumn].isSkipItems = false;
        }

        // [Part 7] Detect/store when we are hovering the unused space after the right-most column,
        // so e.g. context menus can react on it. Clear the resizable flag if none of our columns
        // are actually resizable. This will hide the resizing option from the context menu.
        final float unusedX1 =
                Math.max(
                        table.workRect.getLeft(),
                        table.rightMostEnabledColumn >= 0
                                ? table.columns[table.rightMostEnabledColumn].clipRect.getRight()
                                : table.workRect.getLeft());
        if (isHoveringTable && table.hoveredColumnBody == -1 && mouseSkewedX >= unusedX1) {
            table.hoveredColumnBody = columnsCount;
        }
        if (!hasResizable && (table.flags & TableFlags.RESIZABLE) != 0) {
            table.flags &= ~TableFlags.RESIZABLE;
        }

        table.isActiveIDAliveBeforeTable = context.activeIDIsAlive != 0;

        // [Part 8] Lock the actual outer rect/work rect right-most position. This is done late to
        // handle the case of fixed-column tables not claiming more width than they need.
        if (table.rightMostStretchedColumn != -1) {
            table.flags &= ~TableFlags.NO_HOST_EXTEND_X;
        }
        if ((table.flags & TableFlags.NO_HOST_EXTEND_X) != 0) {
            table.outerRect.setRight(unusedX1);
            table.workRect.setRight(unusedX1);
            table.innerClipRect.setRight(Math.min(table.innerClipRect.getRight(), unusedX1));
        }
        table.innerWindow.rectParentWork.set(table.workRect);
        table.borderX1 = table.innerClipRect.getLeft();
        table.borderX2 = table.innerClipRect.getRight();

        // Set up the window's work rect bottom for getContentRegionAvailable(). Other values will
        // be updated in each tableBeginCell() call.
        final float windowContentMaxY;
        if ((table.flags & TableFlags.NO_HOST_EXTEND_Y) != 0) {
            windowContentMaxY = table.outerRect.getBottom();
        } else {
            windowContentMaxY =
                    Math.max(
                            table.innerWindow.rectContent.getBottom(),
                            (table.flags & TableFlags.SCROLL_Y) != 0
                                    ? 0.0f
                                    : table.outerRect.getBottom());
        }
        final RectFloat innerWork = table.innerWindow.rectWork;
        innerWork.setBottom(
                MathUtil.clamp(
                        windowContentMaxY - style.cellPadding.y,
                        innerWork.getTop(),
                        Math.max(innerWork.getTop(), innerWork.getBottom())));

        // [Part 9] Allocate draw channels and set up the background clip rect
        tableSetupDrawChannels(table);

        // [Part 10] Hit testing on borders
        if ((table.flags & TableFlags.RESIZABLE) != 0) {
            tableUpdateBorders(table);
        }
        tableInstance.lastTopHeadersRowHeight = 0.0f;
        table.isLayoutLocked = true;
        table.isUsingHeaders = false;

        // Highlight the header
        table.highlightColumnHeader = -1;
        if (table.isContextPopupOpen
                && table.contextPopupColumn != -1
                && table.instanceInteracted == table.instanceCurrent) {
            table.highlightColumnHeader = table.contextPopupColumn;
        } else if ((table.flags & TableFlags.HIGHLIGHT_HOVERED_COLUMN) != 0
                && table.hoveredColumnBody != -1
                && table.hoveredColumnBody != columnsCount
                && table.hoveredColumnBorder == -1
                && (context.activeID == 0 || table.isActiveIDInTable || context.dragDropActive)) {
            table.highlightColumnHeader = table.hoveredColumnBody;
        }

        // [Part 11] Default context menu
        // - To append to this menu: you can call tableBeginContextMenuPopup()/.../endPopup().
        // - To modify or replace this: set table.disableDefaultContextMenu = true, then call
        //   tableBeginContextMenuPopup()/.../endPopup().
        // - You may call tableDrawDefaultContextMenu() with selected flags to display specific
        //   sections of the default menu.
        if (!table.disableDefaultContextMenu
                && IkGuiImplTableHeaders.tableBeginContextMenuPopup(table)) {
            IkGuiImplTableHeaders.tableDrawDefaultContextMenu(table, table.flags);
            IkGuiImplPopups.endPopup();
        }

        // [Part 12] Sanitize and build sort specs before we have a chance to use them for display.
        // This path will only be exercised when sort specs are modified before header rows (e.g.
        // init or a visibility change).
        if (table.isSortSpecsDirty && (table.flags & TableFlags.SORTABLE) != 0) {
            tableSortSpecsBuild(table);
        }

        // [Part 13] Set up the inner window decoration size (for scrolling / nav tracking to take
        // frozen rows/columns into account)
        if (table.freezeColumnsRequest > 0) {
            table.innerWindow.decoInnerSizeX1 =
                    table.columns[table.displayOrderToIndex[table.freezeColumnsRequest - 1]].maxX
                            - table.outerRect.getLeft();
        }
        if (table.freezeRowsRequest > 0) {
            table.innerWindow.decoInnerSizeY1 = tableInstance.lastFrozenHeight;
        }
        tableInstance.lastFrozenHeight = 0.0f;

        final Window innerWindow = table.innerWindow;
        final BoxSelectState bs = context.boxSelectState;
        if (bs.window == innerWindow && bs.unclipMode) {
            tableApplyExternalUnclipRect(table, bs.unclipRect);
        }

        // Initial state
        if ((table.flags & TableFlags.NO_CLIP) != 0) {
            table.drawSplitter.setCurrentChannel(innerWindow.drawList, TABLE_DRAW_CHANNEL_NOCLIP);
        } else {
            innerWindow.drawList.pushClipRect(
                    innerWindow.rectInnerClip.getLeft(),
                    innerWindow.rectInnerClip.getTop(),
                    innerWindow.rectInnerClip.getRight(),
                    innerWindow.rectInnerClip.getBottom(),
                    false);
        }
    }

    /**
     * Mark columns as requesting output if they overlap an external unclip rect, e.g. for box
     * selection.
     *
     * @param table The table.
     * @param rect The unclip rect.
     */
    static void tableApplyExternalUnclipRect(@NonNull Table table, @NonNull RectFloat rect) {
        if (rect.getLeft() > rect.getRight() || rect.getTop() > rect.getBottom()) {
            return;
        }
        for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
            final TableColumn column = table.columns[columnIndex];
            if (!column.isRequestOutput
                    && rect.overlaps(
                            new RectFloat(
                                    column.minX,
                                    table.workRect.getTop(),
                                    column.maxX,
                                    Float.MAX_VALUE))) {
                column.isRequestOutput = true;
            }
        }
    }

    /**
     * Process hit testing on resizing borders. The actual size change is applied in endTable().
     *
     * @param table The table.
     */
    static void tableUpdateBorders(@NonNull Table table) {
        if ((table.flags & TableFlags.RESIZABLE) == 0) {
            IkGuiImplDebugTools.reportError(log, "tableUpdateBorders() requires a resizable table");
            return;
        }

        // At this point the outer rect height may be zero or under the actual final height, so we
        // rely on temporal coherency and use the final height from last frame. This only affects
        // interaction with columns, the visuals are displayed in endTable().
        final TableInstanceData tableInstance = table.getInstanceData(table.instanceCurrent);
        final float hitHalfWidth = IkGuiInternal.truncate(TABLE_RESIZE_SEPARATOR_HALF_THICKNESS);
        final float hitY1 =
                (table.freezeRowsCount >= 1 ? table.outerRect.getTop() : table.workRect.getTop())
                        + table.angledHeadersHeight;
        final float hitY2Body =
                Math.max(
                        table.outerRect.getBottom(),
                        hitY1 + tableInstance.lastOuterHeight - table.angledHeadersHeight);
        final float hitY2Head = hitY1 + tableInstance.lastTopHeadersRowHeight;

        for (int order = 0; order < table.columnsCount; ++order) {
            if (!table.enabledMaskByDisplayOrder.get(order)) {
                continue;
            }

            final int columnIndex = table.displayOrderToIndex[order];
            final TableColumn column = table.columns[columnIndex];
            if ((column.flags
                            & (TableColumnFlags.NO_RESIZE
                                    | TableColumnFlags.INTERNAL_NO_DIRECT_RESIZE))
                    != 0) {
                continue;
            }

            // NO_BORDERS_IN_BODY_UNTIL_RESIZE will be honored in tableDrawBorders()
            final float borderY2Hit =
                    (table.flags & TableFlags.NO_BORDERS_IN_BODY) != 0 ? hitY2Head : hitY2Body;
            if ((table.flags & TableFlags.NO_BORDERS_IN_BODY) != 0 && !table.isUsingHeaders) {
                continue;
            }

            if (!column.isVisibleX && table.lastResizedColumn != columnIndex) {
                continue;
            }

            final int columnID = tableGetColumnResizeID(table, columnIndex, table.instanceCurrent);
            final RectFloat hitRect =
                    new RectFloat(
                            column.maxX - hitHalfWidth,
                            hitY1,
                            column.maxX + hitHalfWidth,
                            borderY2Hit);
            IkGuiInternal.itemAdd(hitRect, columnID, null, ItemFlags.NO_NAV);

            final IkBoolean hovered = new IkBoolean();
            final IkBoolean held = new IkBoolean();
            final boolean pressed =
                    IkGuiInternal.buttonBehavior(
                            hitRect,
                            columnID,
                            hovered,
                            held,
                            ButtonFlags.INTERNAL_FLATTEN_CHILDREN
                                    | ButtonFlags.INTERNAL_PRESSED_ON_CLICK
                                    | ButtonFlags.INTERNAL_PRESSED_ON_DOUBLE_CLICK
                                    | ButtonFlags.INTERNAL_NO_NAV_FOCUS);
            if (pressed && IkGuiImplUtils.isMouseDoubleClicked(MouseButton.LEFT)) {
                tableSetColumnWidthAutoSingle(table, columnIndex);
                IkGuiInternal.clearActiveID();
                held.set(false);
            }
            if (held.get()) {
                if (table.lastResizedColumn == -1) {
                    table.resizeLockMinContentsX2 =
                            table.rightMostEnabledColumn != -1
                                    ? table.columns[table.rightMostEnabledColumn].maxX
                                    : -Float.MAX_VALUE;
                }
                table.resizedColumn = columnIndex;
                table.instanceInteracted = table.instanceCurrent;
            }
            if ((hovered.get() && context.hoveredIDTimer > TABLE_RESIZE_SEPARATOR_FEEDBACK_TIMER)
                    || held.get()) {
                table.hoveredColumnBorder = columnIndex;
                IkGuiImplUtils.setMouseCursor(MouseCursor.RESIZE_EW);
            }
        }
    }

    /** Only call endTable() if beginTable() returns true. */
    public static void endTable() {
        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "endTable() call should only be done while in beginTable() scope!");
            return;
        }

        // If the user never got to call tableNextRow() or tableNextColumn(), we call layout
        // ourselves to ensure all our code paths are consistent and borders get drawn
        if (!table.isLayoutLocked) {
            tableUpdateLayout(table);
        }

        final int flags = table.flags;
        final Window innerWindow = table.innerWindow;
        final Window outerWindow = table.outerWindow;
        TableTempData tempData = table.tempData;
        if (innerWindow != context.windowCurrent || innerWindow.id != tempData.windowID) {
            IkGuiImplDebugTools.reportError(log, "endTable() called in the wrong window");
        }

        if (table.isInsideRow) {
            tableEndRow(table);
        }

        // Context menu in the column body
        if ((flags & TableFlags.CONTEXT_MENU_IN_BODY) != 0
                && table.hoveredColumnBody != -1
                && !IkGuiImplUtils.isAnyItemHovered()
                && IkGuiImplUtils.isMouseReleased(MouseButton.RIGHT)) {
            IkGuiImplTableHeaders.tableOpenContextMenu(table.hoveredColumnBody);
        }

        // Finalize the table height
        final TableInstanceData tableInstance = table.getInstanceData(table.instanceCurrent);
        innerWindow.lineSizePrevious.set(tempData.hostBackupPrevLineSize);
        innerWindow.lineSizeCurrent.set(tempData.hostBackupCurrLineSize);
        innerWindow.cursorMaxPosition.set(tempData.hostBackupCursorMaxPos);
        // Rounding the final position is important as we currently don't round row heights
        final float innerContentMaxY = (float) Math.ceil(table.rowPosY2);
        if (innerWindow != outerWindow) {
            innerWindow.cursorMaxPosition.y = innerContentMaxY;
        } else if ((flags & TableFlags.NO_HOST_EXTEND_Y) == 0) {
            // Patch the outer rect/inner rect height
            final float bottom = Math.max(table.outerRect.getBottom(), innerContentMaxY);
            table.outerRect.setBottom(bottom);
            table.innerRect.setBottom(bottom);
        }
        table.workRect.setBottom(Math.max(table.workRect.getBottom(), table.outerRect.getBottom()));
        tableInstance.lastOuterHeight = table.outerRect.getHeight();

        // Set up the inner scrolling range
        if ((table.flags & TableFlags.SCROLL_X) != 0) {
            final float outerPaddingForBorder =
                    (table.flags & TableFlags.BORDERS_OUTER_V) != 0 ? TABLE_BORDER_SIZE : 0.0f;
            float maxPosX = innerWindow.cursorMaxPosition.x;
            if (table.rightMostEnabledColumn != -1) {
                maxPosX =
                        Math.max(
                                maxPosX,
                                table.columns[table.rightMostEnabledColumn].workMaxX
                                        + table.cellPaddingX
                                        + table.outerPaddingX
                                        - outerPaddingForBorder);
            }
            if (table.resizedColumn != -1) {
                maxPosX = Math.max(maxPosX, table.resizeLockMinContentsX2);
            }
            innerWindow.cursorMaxPosition.x = maxPosX + table.tempData.angledHeadersExtraWidth;
        }

        // Pop the clipping rect
        if ((flags & TableFlags.NO_CLIP) == 0) {
            innerWindow.drawList.popClipRect();
        }
        innerWindow.rectCurrentClip.set(
                innerWindow.drawList.getClipRectMinX(),
                innerWindow.drawList.getClipRectMinY(),
                innerWindow.drawList.getClipRectMaxX(),
                innerWindow.drawList.getClipRectMaxY());

        // Draw borders
        if ((flags & TableFlags.BORDERS) != 0) {
            tableDrawBorders(table);
        }

        // Flatten channels. Unlike ImGui we don't need to reorder channels to merge draw calls.
        final DrawListSplitter splitter = table.drawSplitter;
        splitter.setCurrentChannel(innerWindow.drawList, 0);
        splitter.merge(innerWindow.drawList);

        // Update the auto-fit width to get ahead of a host using our size to auto-resize without
        // waiting for the next beginTable()
        float autoFitWidthForFixed = 0.0f;
        float autoFitWidthForStretched = 0.0f;
        float autoFitWidthForStretchedMin = 0.0f;
        for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
            if (!table.enabledMaskByIndex.get(columnIndex)) {
                continue;
            }
            final TableColumn column = table.columns[columnIndex];
            final float columnWidthRequest =
                    (column.flags & TableColumnFlags.WIDTH_FIXED) != 0
                                    && (column.flags & TableColumnFlags.NO_RESIZE) == 0
                            ? column.widthRequest
                            : tableGetColumnWidthAuto(table, column);
            if ((column.flags & TableColumnFlags.WIDTH_FIXED) != 0) {
                autoFitWidthForFixed += columnWidthRequest;
            } else {
                autoFitWidthForStretched += columnWidthRequest;
            }
            if ((column.flags & TableColumnFlags.WIDTH_STRETCH) != 0
                    && (column.flags & TableColumnFlags.NO_RESIZE) != 0) {
                autoFitWidthForStretchedMin =
                        Math.max(
                                autoFitWidthForStretchedMin,
                                columnWidthRequest
                                        / (column.stretchWeight / table.columnsStretchSumWeights));
            }
        }
        final float widthSpacings =
                table.outerPaddingX * 2.0f
                        + (table.cellSpacingX1 + table.cellSpacingX2)
                                * (table.columnsEnabledCount - 1);
        table.columnsAutoFitWidth =
                widthSpacings
                        + (table.cellPaddingX * 2.0f) * table.columnsEnabledCount
                        + autoFitWidthForFixed
                        + Math.max(autoFitWidthForStretched, autoFitWidthForStretchedMin);

        // Update scroll
        if ((table.flags & TableFlags.SCROLL_X) == 0 && innerWindow != outerWindow) {
            innerWindow.scrollPosition.x = 0.0f;
        } else if (table.lastResizedColumn != -1
                && table.resizedColumn == -1
                && innerWindow.scrollbarX
                && table.instanceInteracted == table.instanceCurrent) {
            // When releasing a column being resized, scroll to keep the resulting column in sight
            final float neighborWidthToKeepVisible =
                    table.minColumnWidth + table.cellPaddingX * 2.0f;
            final TableColumn column = table.columns[table.lastResizedColumn];
            if (column.maxX < table.innerClipRect.getLeft()) {
                IkGuiInternal.setScrollFromPosX(
                        innerWindow,
                        column.maxX - innerWindow.position.x - neighborWidthToKeepVisible,
                        1.0f);
            } else if (column.maxX > table.innerClipRect.getRight()) {
                IkGuiInternal.setScrollFromPosX(
                        innerWindow,
                        column.maxX - innerWindow.position.x + neighborWidthToKeepVisible,
                        1.0f);
            }
        }

        // Apply resizing/dragging at the end of the frame
        if (table.resizedColumn != -1 && table.instanceCurrent == table.instanceInteracted) {
            final TableColumn column = table.columns[table.resizedColumn];
            final float newX2 =
                    context.io.mousePosition.x
                            - context.activeIDClickOffset.x
                            + IkGuiInternal.truncate(TABLE_RESIZE_SEPARATOR_HALF_THICKNESS);
            final float newWidth =
                    IkGuiInternal.truncate(
                            newX2 - column.minX - table.cellSpacingX1 - table.cellPaddingX * 2.0f);
            table.resizedColumnNextWidth = newWidth;
        }

        table.isActiveIDInTable = context.activeIDIsAlive != 0 && !table.isActiveIDAliveBeforeTable;

        // Pop from the ID stack
        if (innerWindow.idStack.peek() != tableInstance.tableInstanceID) {
            IkGuiImplDebugTools.reportError(log, "Mismatching pushID()/popID() inside a table!");
        }
        if (outerWindow.itemWidthStack.size() < tempData.hostBackupItemWidthStackSize) {
            IkGuiImplDebugTools.reportError(log, "Too many popItemWidth() calls inside a table!");
        }
        if (table.instanceCurrent > 0) {
            IkGuiImplUtils.popID();
        }
        IkGuiImplUtils.popID();

        // Restore window data that we modified
        final Vector2f backupOuterMaxPos = new Vector2f(outerWindow.cursorMaxPosition);
        innerWindow.rectWork.set(tempData.hostBackupWorkRect);
        innerWindow.rectParentWork.set(tempData.hostBackupParentWorkRect);
        innerWindow.skipItems = table.hostSkipItems;
        outerWindow.cursorPosition.set(table.outerRect.getLeft(), table.outerRect.getTop());
        outerWindow.columnsOffset = tempData.hostBackupColumnsOffset;
        outerWindow.currentItemWidth = tempData.hostBackupItemWidth;
        while (outerWindow.itemWidthStack.size() > tempData.hostBackupItemWidthStackSize) {
            outerWindow.itemWidthStack.pop();
        }

        // Layout in the outer window. To allow auto-fit and the desirable effect of sameLine(), we
        // dissociate the used vs ideal size by overriding the previous line position and cursor max
        // position manually.
        if (innerWindow != outerWindow) {
            final int backupNavLayersActiveMask = innerWindow.navLayersActiveMask;
            // So empty tables don't appear to navigate differently
            innerWindow.navLayersActiveMask |= 1 << table.navLayer;
            // To avoid error recovery recursing
            context.currentTable = null;
            IkGuiImplWindows.endChild();
            context.currentTable = table;
            innerWindow.navLayersActiveMask = backupNavLayersActiveMask;
        } else {
            innerWindow.treeDepth--;
            IkGuiInternal.itemSize(table.outerRect.getWidth(), table.outerRect.getHeight());
            IkGuiInternal.itemAdd(table.outerRect, 0);
        }

        // Override the declared contents width/height to enable auto-resize while not needlessly
        // adding a scrollbar
        if ((table.flags & TableFlags.NO_HOST_EXTEND_X) != 0) {
            // The auto-fit width may be one frame ahead here since for fixed + no resize it is
            // calculated from the latest contents
            outerWindow.cursorMaxPosition.x =
                    Math.max(
                            backupOuterMaxPos.x,
                            table.outerRect.getLeft() + table.columnsAutoFitWidth);
        } else if (tempData.userOuterSize.x <= 0.0f) {
            final float outerContentMaxX = table.outerRect.getLeft() + table.columnsAutoFitWidth;
            final float decorationSize =
                    table.tempData.angledHeadersExtraWidth
                            + (innerWindow != outerWindow ? innerWindow.scrollbarSizes.x : 0.0f);
            outerWindow.cursorIdealMaxPosition.x =
                    Math.max(
                            outerWindow.cursorIdealMaxPosition.x,
                            outerContentMaxX + decorationSize - tempData.userOuterSize.x);
            outerWindow.cursorMaxPosition.x =
                    Math.max(
                            backupOuterMaxPos.x,
                            Math.min(
                                    table.outerRect.getRight(), outerContentMaxX + decorationSize));
        } else {
            outerWindow.cursorMaxPosition.x =
                    Math.max(backupOuterMaxPos.x, table.outerRect.getRight());
        }
        if (tempData.userOuterSize.y <= 0.0f) {
            final float outerContentSizeY =
                    innerWindow == outerWindow
                            ? innerContentMaxY - table.innerRect.getTop()
                            : innerContentMaxY - innerWindow.cursorStartPosition.y;
            final float outerContentMaxY = table.outerRect.getTop() + outerContentSizeY;
            final float decorationSize =
                    innerWindow != outerWindow ? innerWindow.scrollbarSizes.y : 0.0f;
            outerWindow.cursorIdealMaxPosition.y =
                    Math.max(
                            outerWindow.cursorIdealMaxPosition.y,
                            outerContentMaxY + decorationSize - tempData.userOuterSize.y);
            outerWindow.cursorMaxPosition.y =
                    Math.max(
                            backupOuterMaxPos.y,
                            Math.min(
                                    table.outerRect.getBottom(),
                                    outerContentMaxY + decorationSize));
        } else {
            // The outer rect bottom may already have been pushed downward from the initial value
            // (unless NO_HOST_EXTEND_Y is set)
            outerWindow.cursorMaxPosition.y =
                    Math.max(backupOuterMaxPos.y, table.outerRect.getBottom());
        }

        // Save settings
        if (table.isSettingsDirty) {
            IkGuiImplTableSettings.tableSaveSettings(table);
        }
        table.isInitializing = false;

        // Clear or restore the current table, if any
        if (context.windowCurrent != outerWindow || context.currentTable != table) {
            IkGuiImplDebugTools.reportError(log, "endTable() window or table mismatch");
        }
        tempData =
                --context.tablesTempDataStacked > 0
                        ? context.tablesTempData.get(context.tablesTempDataStacked - 1)
                        : null;
        context.currentTable =
                tempData != null && tempData.windowID == outerWindow.id
                        ? context.tables.get(tempData.tableIndex)
                        : null;
        if (context.currentTable != null) {
            context.currentTable.tempData = tempData;
            context.currentTable.drawSplitter = tempData.drawSplitter;
        }
        outerWindow.currentTableIndex =
                context.currentTable != null ? context.tables.indexOf(context.currentTable) : -1;
        outerWindow.navIsScrollPushableX = context.currentTable == null;
    }

    /**
     * Initialize column defaults, called in tableUpdateLayout() when initializing or after loading
     * settings.
     *
     * @param table The table.
     * @param column The column.
     * @param initMask Which fields to initialize, using the RESIZABLE, REORDERABLE, HIDEABLE, and
     *     SORTABLE table flags.
     */
    static void tableInitColumnDefaults(
            @NonNull Table table, @NonNull TableColumn column, int initMask) {
        final int flags = column.flags;
        if ((initMask & TableFlags.RESIZABLE) != 0) {
            final float initWidthOrWeight = column.initStretchWeightOrWidth;
            column.widthRequest =
                    (flags & TableColumnFlags.WIDTH_FIXED) != 0 && initWidthOrWeight > 0.0f
                            ? initWidthOrWeight
                            : -1.0f;
            column.stretchWeight =
                    initWidthOrWeight > 0.0f && (flags & TableColumnFlags.WIDTH_STRETCH) != 0
                            ? initWidthOrWeight
                            : -1.0f;
            // Disable auto-fit if an explicit width/weight has been specified
            if (initWidthOrWeight > 0.0f) {
                column.autoFitQueue = 0;
            }
        }
        if ((initMask & TableFlags.REORDERABLE) != 0) {
            column.displayOrder =
                    (table.flags & TableFlags.REORDERABLE) != 0 ? -1 : indexOf(table, column);
        }
        if ((initMask & TableFlags.HIDEABLE) != 0) {
            final boolean enabled = (flags & TableColumnFlags.DEFAULT_HIDE) == 0;
            column.isUserEnabled = enabled;
            column.isUserEnabledNextFrame = enabled;
        }
        if ((initMask & TableFlags.SORTABLE) != 0) {
            // Multiple columns using DEFAULT_SORT will be reassigned unique sort order values when
            // building the sort specs
            final boolean defaultSort = (flags & TableColumnFlags.DEFAULT_SORT) != 0;
            column.sortOrder = defaultSort ? 0 : -1;
            column.sortDirection =
                    defaultSort
                            ? (flags & TableColumnFlags.PREFER_SORT_DESCENDING) != 0
                                    ? SortDirection.DESCENDING
                                    : SortDirection.ASCENDING
                            : SortDirection.NONE;
        }
    }

    /**
     * Find the index of a column in a table.
     *
     * @param table The table.
     * @param column The column.
     * @return The index, or -1 if the column is not in the table.
     */
    private static int indexOf(@NonNull Table table, @NonNull TableColumn column) {
        for (int n = 0; n < table.columns.length; ++n) {
            if (table.columns[n] == column) {
                return n;
            }
        }
        return -1;
    }

    /**
     * Apply column setup data.
     *
     * @param table The table.
     * @param index The column index.
     * @param id The column ID.
     * @param name The column name, may be null.
     * @param flags The column flags.
     * @param initWidthOrWeight The initial width or weight, ignored if 0 or less.
     * @param userData The user data.
     */
    private static void tableSetupColumnApply(
            @NonNull Table table,
            int index,
            int id,
            String name,
            int flags,
            float initWidthOrWeight,
            int userData) {
        final TableColumn column = table.columns[index];

        // Error when passing a width or weight if the policy is entirely left to the default, to
        // avoid storing a width into a weight and vice versa. Give grace to users of SCROLL_X.
        if (table.isDefaultSizingPolicy
                && (flags & TableColumnFlags.INTERNAL_WIDTH_MASK) == 0
                && (table.flags & TableFlags.SCROLL_X) == 0
                && initWidthOrWeight > 0.0f) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "tableSetupColumn(): can only specify a width/weight if the sizing policy is set"
                            + " explicitly in either the table or the column");
            return;
        }

        // When passing a width, automatically enforce the WIDTH_FIXED policy (whereas
        // tableSetupColumnFlags() would default to auto width if the table is not resizable)
        if ((flags & TableColumnFlags.INTERNAL_WIDTH_MASK) == 0 && initWidthOrWeight > 0.0f) {
            final int sizing = table.flags & TableFlags.INTERNAL_SIZING_MASK;
            if (sizing == TableFlags.SIZING_FIXED_FIT || sizing == TableFlags.SIZING_FIXED_SAME) {
                flags |= TableColumnFlags.WIDTH_FIXED;
            }
        }
        if ((flags & TableColumnFlags.ANGLED_HEADER) != 0) {
            flags |= TableColumnFlags.NO_HEADER_LABEL;
            table.angledHeadersCount++;
        }

        tableSetupColumnFlags(table, column, flags);
        column.id = id;
        column.userData = userData;
        column.name = name;
        column.initStretchWeightOrWidth = initWidthOrWeight;
    }

    /**
     * Set up a column. Call before the first row.
     *
     * @param label The column label, may be null or empty.
     * @param flags The column flags.
     * @param initWidthOrWeight The initial width (for fixed columns) or weight (for stretch
     *     columns), ignored if 0 or less.
     * @param userData User data, which is returned in the sort specs.
     * @see TableColumnFlags
     */
    public static void tableSetupColumn(
            String label, int flags, float initWidthOrWeight, int userData) {
        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetupColumn() should only be called inside beginTable()!");
            return;
        }
        if (table.declColumnsCount >= table.columnsCount) {
            IkGuiImplDebugTools.reportError(log, "tableSetupColumn() called too many times!");
            return;
        }
        if (table.isLayoutLocked) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetupColumn() needs to be called before the first row!");
            return;
        }
        if ((flags & TableColumnFlags.INTERNAL_STATUS_MASK) != 0) {
            IkGuiImplDebugTools.reportError(
                    log, "Illegal to pass status flags to tableSetupColumn()");
            flags &= ~TableColumnFlags.INTERNAL_STATUS_MASK;
        }

        final String name = label != null && !label.isEmpty() ? label : null;
        final int columnID = name != null ? Hash.getID(name) : 0;

        // When the ID changed or a column moved, defer the request until layout, where we will
        // process a full reconcile
        final int columnIndex = table.declColumnsCount++;
        final TableColumn column = table.columns[columnIndex];

        // If the topology changes go into reconcile mode
        if (!table.isReconcileMode && column.id != columnID && !table.isNewTable) {
            table.isReconcileMode = true;
        }

        // Fast/common path
        if (!table.isReconcileMode) {
            tableSetupColumnApply(
                    table, columnIndex, columnID, name, flags, initWidthOrWeight, userData);
            column.isNeedReconcileSrc = false;
            column.isNeedReconcileDst = false;
            final TableColumn[] oldColumns = table.tempData.oldColumnsData;
            if (oldColumns != null && columnIndex < oldColumns.length) {
                oldColumns[columnIndex].isNeedReconcileSrc = false;
            }
            return;
        }

        // Reconcile path: defer applying data to tableUpdateLayout() -> tableReconcileColumns()
        // -> tableSetupColumnApply()
        final TableReconcileColumnData reconcileData = new TableReconcileColumnData();
        reconcileData.id = columnID;
        reconcileData.name = name;
        reconcileData.flags = flags;
        reconcileData.initWidthOrWeight = initWidthOrWeight;
        reconcileData.userData = userData;
        reconcileData.columnNewIndex = columnIndex;
        reconcileData.columnOldIndex = -1;
        table.tempData.reconcileColumnsRequests.add(reconcileData);
        // Allow tableGetColumnName() to work before layout
        column.name = name;
        column.isNeedReconcileSrc = true;
        column.isNeedReconcileDst = true;
    }

    /**
     * Match columns submitted with a changed topology to columns from the previous frame.
     *
     * @param table The table.
     */
    static void tableReconcileColumns(@NonNull Table table) {
        IkGuiImplDebugTools.debugLog(
                DebugLogFlags.EVENT_TABLE, "[table] Reconcile columns for table 0x%08X", table.id);
        final TableTempData tempData = table.tempData;
        final TableColumn[] dstColumns = table.columns;
        final TableColumn[] srcColumns =
                tempData.oldColumnsData == null ? table.columns : tempData.oldColumnsData;

        // Find matches for named columns
        int matches = 0;
        final var requests = tempData.reconcileColumnsRequests;
        for (TableReconcileColumnData reconcileData : requests) {
            if (reconcileData.id == 0) {
                continue;
            }
            for (int srcIndex = 0; srcIndex < srcColumns.length; ++srcIndex) {
                final TableColumn srcColumn = srcColumns[srcIndex];
                if (srcColumn.id == reconcileData.id && srcColumn.isNeedReconcileSrc) {
                    final TableColumn dstColumn = dstColumns[reconcileData.columnNewIndex];
                    srcColumn.isNeedReconcileSrc = false;
                    dstColumn.isNeedReconcileDst = false;
                    reconcileData.columnOldIndex = srcIndex;
                    reconcileData.columnOldData.set(srcColumn);
                    matches++;
                    break;
                }
            }
        }

        // Remaining entries are matched sequentially
        int dstIndex = 0;
        if (matches != requests.size()) {
            for (int srcIndex = 0; srcIndex < srcColumns.length; ++srcIndex) {
                final TableColumn srcColumn = srcColumns[srcIndex];
                if (!srcColumn.isNeedReconcileSrc) {
                    continue;
                }
                while (dstIndex < requests.size() && requests.get(dstIndex).columnOldIndex != -1) {
                    dstIndex++;
                }
                if (dstIndex == requests.size()) {
                    break;
                }
                final TableReconcileColumnData reconcileData = requests.get(dstIndex);
                final TableColumn dstColumn = dstColumns[reconcileData.columnNewIndex];
                srcColumn.isNeedReconcileSrc = false;
                dstColumn.isNeedReconcileDst = false;
                reconcileData.columnOldIndex = srcIndex;
                reconcileData.columnOldData.set(srcColumn);
            }
        }

        // Apply in the final pass. Because the source columns may be the table columns, we went
        // through a temporary copy. When the old column was not found we clear it anyway, with
        // default data (which will set isJustCreated).
        for (TableReconcileColumnData reconcileData : requests) {
            table.columns[reconcileData.columnNewIndex].set(reconcileData.columnOldData);
            tableSetupColumnApply(
                    table,
                    reconcileData.columnNewIndex,
                    reconcileData.id,
                    reconcileData.name,
                    reconcileData.flags,
                    reconcileData.initWidthOrWeight,
                    reconcileData.userData);
        }
        tableFixDisplayOrder(table);
        table.isSettingsDirty = true;
        table.isReconcileMode = false;
        requests.clear();
    }

    /**
     * Lock columns/rows so they stay visible when scrolled.
     *
     * @param columns The number of columns to freeze.
     * @param rows The number of rows to freeze.
     */
    public static void tableSetupScrollFreeze(int columns, int rows) {
        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetupScrollFreeze() should only be called inside beginTable()!");
            return;
        }
        if (table.isLayoutLocked) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetupScrollFreeze() needs to be called before the first row!");
            return;
        }
        if (columns < 0 || columns >= TABLE_MAX_COLUMNS || rows < 0 || rows >= 128) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetupScrollFreeze() invalid columns {} or rows {}", columns, rows);
            return;
        }

        table.freezeColumnsRequest =
                (table.flags & TableFlags.SCROLL_X) != 0
                        ? Math.min(columns, table.columnsCount)
                        : 0;
        table.freezeColumnsCount =
                table.innerWindow.scrollPosition.x != 0.0f ? table.freezeColumnsRequest : 0;
        table.freezeRowsRequest = (table.flags & TableFlags.SCROLL_Y) != 0 ? rows : 0;
        table.freezeRowsCount =
                table.innerWindow.scrollPosition.y != 0.0f ? table.freezeRowsRequest : 0;
        // Make sure this is set before tableUpdateLayout() so the list clipper can benefit from it
        table.isUnfrozenRows = table.freezeRowsCount == 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Simple accessors
    // ---------------------------------------------------------------------------------------------

    /**
     * The number of columns in the current table.
     *
     * @return The number of columns passed to beginTable(), or 0 outside of a table.
     */
    public static int tableGetColumnCount() {
        final Table table = context.currentTable;
        return table != null ? table.columnsCount : 0;
    }

    /**
     * The name of a column in the current table.
     *
     * @param columnIndex The column index, or -1 for the current column.
     * @return The name, "" if the column didn't have a name, or null outside of a table.
     */
    public static String tableGetColumnName(int columnIndex) {
        final Table table = context.currentTable;
        if (table == null) {
            return null;
        }
        if (columnIndex < 0) {
            columnIndex = table.currentColumn;
        }
        return tableGetColumnName(table, columnIndex);
    }

    /**
     * The name of a column.
     *
     * @param table The table.
     * @param columnIndex The column index.
     * @return The name, or "" if the column didn't have a name.
     */
    static String tableGetColumnName(@NonNull Table table, int columnIndex) {
        if (columnIndex < 0 || columnIndex >= table.columnsCount) {
            return "";
        }
        // The name is invalid at this point
        if (!table.isLayoutLocked && columnIndex >= table.declColumnsCount) {
            return "";
        }
        final String name = table.columns[columnIndex].name;
        return name == null ? "" : name;
    }

    /**
     * Change the user accessible enabled/disabled state of a column. Set to false to hide the
     * column. The user can use the context menu to change this themselves. Requires
     * TableFlags.HIDEABLE. The request is applied during the next layout, which happens on the
     * first call to tableNextRow() after beginTable().
     *
     * @param columnIndex The column index, or -1 for the current column.
     * @param enabled Whether the column should be enabled.
     */
    public static void tableSetColumnEnabled(int columnIndex, boolean enabled) {
        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetColumnEnabled() should only be called inside beginTable()!");
            return;
        }
        if ((table.flags & TableFlags.HIDEABLE) == 0) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetColumnEnabled() requires TableFlags.HIDEABLE");
            return;
        }
        if (columnIndex < 0) {
            columnIndex = table.currentColumn;
        }
        if (columnIndex < 0 || columnIndex >= table.columnsCount) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetColumnEnabled() invalid column index {}", columnIndex);
            return;
        }
        table.columns[columnIndex].isUserEnabledNextFrame = enabled;
    }

    /**
     * Column flags, including the enabled/visible/sorted/hovered status flags. We allow querying an
     * extra column to poll the hovered state of the right-most section.
     *
     * @param columnIndex The column index, or -1 for the current column.
     * @return The column flags.
     * @see TableColumnFlags
     */
    public static int tableGetColumnFlags(int columnIndex) {
        final Table table = context.currentTable;
        if (table == null) {
            return TableColumnFlags.NONE;
        }
        if (columnIndex < 0) {
            columnIndex = table.currentColumn;
        }
        if (columnIndex == table.columnsCount) {
            return table.hoveredColumnBody == columnIndex
                    ? TableColumnFlags.IS_HOVERED
                    : TableColumnFlags.NONE;
        }
        if (columnIndex < 0 || columnIndex >= table.columnsCount) {
            return TableColumnFlags.NONE;
        }
        return table.columns[columnIndex].flags;
    }

    /**
     * The cell rectangle based on the currently known height. We generally don't know the row
     * height until the end of the row, so the bottom will be incorrect in many situations.
     *
     * @param table The table.
     * @param columnIndex The column index.
     * @param output Where to store the result.
     * @return The output, for convenience.
     */
    static RectFloat tableGetCellBackgroundRect(
            @NonNull Table table, int columnIndex, @NonNull RectFloat output) {
        final TableColumn column = table.columns[columnIndex];
        final float x1 = Math.max(column.minX, table.workRect.getLeft());
        final float x2 = Math.min(column.maxX, table.workRect.getRight());
        output.set(x1, table.rowPosY1, x2, table.rowPosY2);
        return output;
    }

    /**
     * The resizing ID for the right side of the given column.
     *
     * @param table The table.
     * @param columnIndex The column index.
     * @param instanceNumber The table instance.
     * @return The ID.
     */
    static int tableGetColumnResizeID(@NonNull Table table, int columnIndex, int instanceNumber) {
        final int instanceID = table.getInstanceID(instanceNumber);
        return instanceID + 1 + columnIndex;
    }

    /**
     * The hovered column in the current table.
     *
     * @return The hovered column, -1 when the table is not hovered, or the column count if the
     *     unused space to the right of the visible columns is hovered.
     */
    public static int tableGetHoveredColumn() {
        final Table table = context.currentTable;
        return table != null ? table.hoveredColumnBody : -1;
    }

    /**
     * The hovered row in the current table. Unlike tableGetHoveredColumn(), this has a one frame
     * latency in updating the value.
     *
     * @return The hovered row from the previous frame, -1 when not hovered.
     */
    public static int tableGetHoveredRow() {
        final Table table = context.currentTable;
        if (table == null) {
            return -1;
        }
        return table.getInstanceData(table.instanceCurrent).hoveredRowLast;
    }

    /**
     * Change the color of a cell or row.
     *
     * @param target What to change the color of.
     * @param color The color.
     * @param columnIndex The column index for cell backgrounds, or -1 for the current column.
     */
    public static void tableSetBackgroundColor(
            @NonNull TableBackgroundTarget target, int color, int columnIndex) {
        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetBackgroundColor() should only be called inside beginTable()!");
            return;
        }
        if (target == TableBackgroundTarget.NONE) {
            IkGuiImplDebugTools.reportError(log, "tableSetBackgroundColor() requires a target");
            return;
        }

        if (color == COLOR_DISABLE) {
            color = 0;
        }

        // We can't draw the cell or row background immediately as we don't know the row height
        switch (target) {
            case CELL_BACKGROUND -> {
                // Discard
                if (table.rowPosY1 > table.innerClipRect.getBottom()) {
                    return;
                }
                if (columnIndex == -1) {
                    columnIndex = table.currentColumn;
                }
                if (columnIndex < 0 || !table.visibleMaskByIndex.get(columnIndex)) {
                    return;
                }
                if (table.rowCellDataCurrent < 0
                        || table.rowCellData[table.rowCellDataCurrent].column != columnIndex) {
                    table.rowCellDataCurrent++;
                }
                final TableCellData cellData = table.rowCellData[table.rowCellDataCurrent];
                cellData.backgroundColor = color;
                cellData.column = columnIndex;
            }
            case ROW_BACKGROUND_0, ROW_BACKGROUND_1 -> {
                // Discard
                if (table.rowPosY1 > table.innerClipRect.getBottom()) {
                    return;
                }
                if (columnIndex != -1) {
                    IkGuiImplDebugTools.reportError(
                            log, "Row background colors don't use a column index");
                }
                final int index = target == TableBackgroundTarget.ROW_BACKGROUND_1 ? 1 : 0;
                table.rowBackgroundColor[index] = color;
            }
            default ->
                    IkGuiImplDebugTools.reportError(
                            log, "Unexpected table background target {}", target);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Row changes
    // ---------------------------------------------------------------------------------------------

    /**
     * The current row index, header rows are accounted for.
     *
     * @return The current row index, or 0 outside of a table.
     */
    public static int tableGetRowIndex() {
        final Table table = context.currentTable;
        return table != null ? table.currentRow : 0;
    }

    /**
     * Append into the first cell of a new row.
     *
     * @param rowFlags The row flags.
     * @param rowMinHeight The minimum row height, including the top and bottom cell padding.
     * @see TableRowFlags
     */
    public static void tableNextRow(int rowFlags, float rowMinHeight) {
        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableNextRow() should only be called inside beginTable()!");
            return;
        }

        if (!table.isLayoutLocked) {
            tableUpdateLayout(table);
        }
        if (table.isInsideRow) {
            tableEndRow(table);
        }

        table.lastRowFlags = table.rowFlags;
        table.rowFlags = rowFlags;
        table.rowCellPaddingY = context.style.variable.cellPadding.y;
        table.rowMinHeight = rowMinHeight;
        tableBeginRow(table);

        // We honor the minimum row height requested by the user, but can't guarantee a per-row
        // maximum height, because that would require a unique clipping rectangle per cell
        table.rowPosY2 += table.rowCellPaddingY * 2.0f;
        table.rowPosY2 = Math.max(table.rowPosY2, table.rowPosY1 + rowMinHeight);

        // Disable output until the user calls tableNextColumn()
        table.innerWindow.skipItems = true;
    }

    /**
     * Begin a row, only called by tableNextRow().
     *
     * @param table The table.
     */
    static void tableBeginRow(@NonNull Table table) {
        final Window window = table.innerWindow;
        if (table.isInsideRow) {
            IkGuiImplDebugTools.reportError(
                    log, "tableBeginRow() called while already inside a row");
            return;
        }

        // New row
        table.currentRow++;
        table.currentColumn = -1;
        table.rowBackgroundColor[0] = COLOR_DISABLE;
        table.rowBackgroundColor[1] = COLOR_DISABLE;
        table.rowCellDataCurrent = -1;
        table.isInsideRow = true;

        // Begin frozen rows
        float nextY1 = table.rowPosY2;
        if (table.currentRow == 0 && table.freezeRowsCount > 0) {
            nextY1 = table.outerRect.getTop();
            window.cursorPosition.y = nextY1;
        }

        table.rowPosY1 = nextY1;
        table.rowPosY2 = nextY1;
        table.rowTextBaseline = 0.0f;
        // Lock the indent
        table.rowIndentOffsetX = window.indent - table.hostIndentX;

        window.baseOffsetPreviousLine = 0.0f;
        // This allows users to call sameLine() to share the line size between columns
        window.cursorPreviousLinePosition.set(
                window.cursorPosition.x, window.cursorPosition.y + table.rowCellPaddingY);
        // This allows users to call sameLine() to share the line size between columns, and to call
        // it from the first column too
        window.lineSizePrevious.set(0.0f, 0.0f);
        window.lineSizeCurrent.set(0.0f, 0.0f);
        window.sameLine = false;
        window.setPos = false;
        window.cursorMaxPosition.y = nextY1;

        // Making the header background color non-transparent will allow us to overlay it multiple
        // times when handling smooth dragging
        if ((table.rowFlags & TableRowFlags.HEADERS) != 0) {
            tableSetBackgroundColor(
                    TableBackgroundTarget.ROW_BACKGROUND_0,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TABLE_HEADER_BACKGROUND),
                    -1);
            if (table.currentRow == 0) {
                table.isUsingHeaders = true;
            }
        }
    }

    /**
     * End a row, called by tableNextRow() and others.
     *
     * @param table The table.
     */
    static void tableEndRow(@NonNull Table table) {
        final Window window = context.windowCurrent;
        if (window != table.innerWindow) {
            IkGuiImplDebugTools.reportError(log, "tableEndRow() called in the wrong window");
        }
        if (!table.isInsideRow) {
            IkGuiImplDebugTools.reportError(log, "tableEndRow() called outside of a row");
            return;
        }

        if (table.currentColumn != -1) {
            tableEndCell(table);
            table.currentColumn = -1;
        }

        // Logging
        if (context.logEnabled) {
            IkGuiImplLogging.logRenderedText(Float.NaN, "|");
        }

        // Position the cursor at the bottom of our row so it can be used for e.g. clipping
        // calculation. The next call to tableBeginCell() will likely reposition the cursor to take
        // vertical padding into account.
        window.cursorPosition.y = table.rowPosY2;

        // Row background fill
        final float backgroundY1 = table.rowPosY1;
        final float backgroundY2 = table.rowPosY2;
        final boolean unfreezeRowsActual = table.currentRow + 1 == table.freezeRowsCount;
        final boolean unfreezeRowsRequest = table.currentRow + 1 == table.freezeRowsRequest;
        final TableInstanceData tableInstance = table.getInstanceData(table.instanceCurrent);
        if ((table.rowFlags & TableRowFlags.HEADERS) != 0
                && (table.currentRow == 0 || (table.lastRowFlags & TableRowFlags.HEADERS) != 0)) {
            tableInstance.lastTopHeadersRowHeight += backgroundY2 - backgroundY1;
        }

        final boolean isVisible =
                backgroundY2 >= table.innerClipRect.getTop()
                        && backgroundY1 <= table.innerClipRect.getBottom();
        if (isVisible) {
            // Update data for tableGetHoveredRow()
            if (table.hoveredColumnBody != -1
                    && context.io.mousePosition.y >= backgroundY1
                    && context.io.mousePosition.y < backgroundY2
                    && tableInstance.hoveredRowNext < 0) {
                tableInstance.hoveredRowNext = table.currentRow;
            }

            // Decide the background color for the row
            int backgroundColor0 = 0;
            int backgroundColor1 = 0;
            if (table.rowBackgroundColor[0] != COLOR_DISABLE) {
                backgroundColor0 = table.rowBackgroundColor[0];
            } else if ((table.flags & TableFlags.ROW_BACKGROUND) != 0) {
                backgroundColor0 =
                        IkGuiImplUtils.getColorWithGlobalAlpha(
                                (table.rowBackgroundColorCounter & 1) != 0
                                        ? ColorType.TABLE_ROW_BACKGROUND_ALT
                                        : ColorType.TABLE_ROW_BACKGROUND);
            }
            if (table.rowBackgroundColor[1] != COLOR_DISABLE) {
                backgroundColor1 = table.rowBackgroundColor[1];
            }

            // Decide the top border color
            int topBorderColor = 0;
            final float borderSize = TABLE_BORDER_SIZE;
            if (table.currentRow > 0 && (table.flags & TableFlags.BORDERS_INNER_H) != 0) {
                topBorderColor =
                        (table.lastRowFlags & TableRowFlags.HEADERS) != 0
                                ? table.borderColorStrong
                                : table.borderColorLight;
            }

            final boolean drawCellBackgroundColor = table.rowCellDataCurrent >= 0;
            final boolean drawStrongBottomBorder = unfreezeRowsActual;
            if ((backgroundColor0 | backgroundColor1 | topBorderColor) != 0
                    || drawStrongBottomBorder
                    || drawCellBackgroundColor) {
                // We know tableEndRow() is always followed by a change of clipping rectangle
                if ((table.flags & TableFlags.NO_CLIP) == 0) {
                    final RectFloat clip = table.background0ClipRectForDrawCommand;
                    window.drawList.setCurrentClipRect(
                            clip.getLeft(), clip.getTop(), clip.getRight(), clip.getBottom());
                }
                table.drawSplitter.setCurrentChannel(window.drawList, TABLE_DRAW_CHANNEL_BG0);
            }

            // Draw the row background. We clip this ourselves so all backgrounds and borders can
            // share the same clipping rectangle.
            if (backgroundColor0 != 0 || backgroundColor1 != 0) {
                final RectFloat rowRect =
                        new RectFloat(
                                table.workRect.getLeft(),
                                backgroundY1,
                                table.workRect.getRight(),
                                backgroundY2);
                rowRect.clipWith(table.backgroundClipRect);
                if (backgroundColor0 != 0 && rowRect.getTop() < rowRect.getBottom()) {
                    window.drawList.addRectFilled(
                            rowRect.getLeft(),
                            rowRect.getTop(),
                            rowRect.getRight(),
                            rowRect.getBottom(),
                            backgroundColor0);
                }
                if (backgroundColor1 != 0 && rowRect.getTop() < rowRect.getBottom()) {
                    window.drawList.addRectFilled(
                            rowRect.getLeft(),
                            rowRect.getTop(),
                            rowRect.getRight(),
                            rowRect.getBottom(),
                            backgroundColor1);
                }
            }

            // Draw cell background colors
            if (drawCellBackgroundColor) {
                final RectFloat cellBackgroundRect = new RectFloat(0, 0, 0, 0);
                for (int n = 0; n <= table.rowCellDataCurrent; ++n) {
                    // As we render the background here we need to clip things (for layout we
                    // would not)
                    final TableCellData cellData = table.rowCellData[n];
                    final TableColumn column = table.columns[cellData.column];
                    tableGetCellBackgroundRect(table, cellData.column, cellBackgroundRect);
                    cellBackgroundRect.clipWith(table.backgroundClipRect);
                    // So that the first column after a frozen one gets clipped when scrolling
                    cellBackgroundRect.setLeft(
                            Math.max(cellBackgroundRect.getLeft(), column.clipRect.getLeft()));
                    cellBackgroundRect.setRight(
                            Math.min(cellBackgroundRect.getRight(), column.maxX));
                    if (cellBackgroundRect.getTop() < cellBackgroundRect.getBottom()) {
                        window.drawList.addRectFilled(
                                cellBackgroundRect.getLeft(),
                                cellBackgroundRect.getTop(),
                                cellBackgroundRect.getRight(),
                                cellBackgroundRect.getBottom(),
                                cellData.backgroundColor);
                    }
                }
            }

            // Draw the top border
            if (topBorderColor != 0
                    && backgroundY1 >= table.backgroundClipRect.getTop()
                    && backgroundY1 < table.backgroundClipRect.getBottom()) {
                window.drawList.addLineH(
                        table.borderX1, table.borderX2, backgroundY1, topBorderColor, borderSize);
            }

            // Draw the bottom border at the row unfreezing mark (always strong)
            if (drawStrongBottomBorder
                    && backgroundY2 >= table.backgroundClipRect.getTop()
                    && backgroundY2 < table.backgroundClipRect.getBottom()) {
                window.drawList.addLineH(
                        table.borderX1,
                        table.borderX2,
                        backgroundY2,
                        table.borderColorStrong,
                        borderSize);
            }
        }

        // End frozen rows (when we are past the last frozen row line, teleport the cursor and
        // alter the clipping rectangle). We need to do that in tableEndRow() instead of
        // tableBeginRow() so the list clipper can mark the end of the row and get the new cursor
        // position.
        if (unfreezeRowsRequest) {
            for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
                table.columns[columnIndex].navLayerCurrent = table.navLayer;
            }
            final float y0 = Math.max(table.rowPosY2 + 1, table.innerClipRect.getTop());
            tableInstance.lastFrozenHeight = y0 - table.outerRect.getTop();

            if (unfreezeRowsActual) {
                table.isUnfrozenRows = true;

                // The background clip rect starts as the inner clip rect, reduce it now
                final float top = Math.min(y0, table.innerClipRect.getBottom());
                final float bottom = table.innerClipRect.getBottom();
                table.backgroundClipRect.setTop(top);
                table.background2ClipRectForDrawCommand.setTop(top);
                table.backgroundClipRect.setBottom(bottom);
                table.background2ClipRectForDrawCommand.setBottom(bottom);
                table.background2DrawChannelCurrent = table.background2DrawChannelUnfrozen;

                final float rowHeight = table.rowPosY2 - table.rowPosY1;
                table.rowPosY2 =
                        table.workRect.getTop() + table.rowPosY2 - table.outerRect.getTop();
                window.cursorPosition.y = table.rowPosY2;
                table.rowPosY1 = table.rowPosY2 - rowHeight;
                for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
                    final TableColumn column = table.columns[columnIndex];
                    column.drawChannelCurrent = column.drawChannelUnfrozen;
                    column.clipRect.setTop(table.background2ClipRectForDrawCommand.getTop());
                }

                // Update the clip rect ahead of tableBeginCell() so the clipper can access the new
                // clip rect top
                setWindowClipRectBeforeSetChannel(window, table.columns[0].clipRect);
                table.drawSplitter.setCurrentChannel(
                        window.drawList, table.columns[0].drawChannelCurrent);
            }
        }

        if ((table.rowFlags & TableRowFlags.HEADERS) == 0) {
            table.rowBackgroundColorCounter++;
        }
        table.isInsideRow = false;
    }

    // ---------------------------------------------------------------------------------------------
    // Column changes
    // ---------------------------------------------------------------------------------------------

    /**
     * The current column index.
     *
     * @return The current column index, or 0 outside of a table.
     */
    public static int tableGetColumnIndex() {
        final Table table = context.currentTable;
        return table != null ? table.currentColumn : 0;
    }

    /**
     * Append into the specified column.
     *
     * @param columnIndex The column index.
     * @return True when the column is visible. You may skip submitting items based on this, but
     *     shouldn't skip columns that may have the tallest contribution to the row height.
     */
    public static boolean tableSetColumnIndex(int columnIndex) {
        final Table table = context.currentTable;
        if (table == null) {
            return false;
        }

        if (table.currentColumn != columnIndex) {
            if (table.currentColumn != -1) {
                tableEndCell(table);
            }
            if (columnIndex < 0 || columnIndex >= table.columnsCount) {
                IkGuiImplDebugTools.reportError(
                        log, "tableSetColumnIndex() invalid column index {}", columnIndex);
                return false;
            }
            tableBeginCell(table, columnIndex);
        }

        return table.columns[columnIndex].isRequestOutput;
    }

    /**
     * Append into the next column, or the first column of the next row if currently in the last
     * column.
     *
     * @return True when the column is visible.
     */
    public static boolean tableNextColumn() {
        final Table table = context.currentTable;
        if (table == null) {
            return false;
        }

        if (table.isInsideRow && table.currentColumn + 1 < table.columnsCount) {
            if (table.currentColumn != -1) {
                tableEndCell(table);
            }
            tableBeginCell(table, table.currentColumn + 1);
        } else {
            tableNextRow(TableRowFlags.NONE, 0.0f);
            tableBeginCell(table, 0);
        }

        return table.columns[table.currentColumn].isRequestOutput;
    }

    /**
     * Begin a cell, called by tableSetColumnIndex()/tableNextColumn(). This is called very
     * frequently.
     *
     * @param table The table.
     * @param columnIndex The column index.
     */
    static void tableBeginCell(@NonNull Table table, int columnIndex) {
        final TableColumn column = table.columns[columnIndex];
        final Window window = table.innerWindow;
        table.currentColumn = columnIndex;

        // The start position is roughly the cell rect min + cell padding + indent
        float startX = column.workMinX;
        if ((column.flags & TableColumnFlags.INDENT_ENABLE) != 0) {
            // Like adding the window indent minus the host indent, but locked for the row
            startX += table.rowIndentOffsetX;
        }

        window.cursorPosition.x = startX;
        window.cursorPosition.y = table.rowPosY1 + table.rowCellPaddingY;
        window.columnsOffset = startX - window.position.x - window.indent;
        window.cursorMaxPosition.x = window.cursorPosition.x;
        // The previous line y is preserved. This allows users to call sameLine() to share the
        // line size between columns.
        window.cursorPreviousLinePosition.x = window.cursorPosition.x;
        window.baseOffsetCurrentLine = table.rowTextBaseline;
        window.navLayerCurrent = column.navLayerCurrent;

        // The work rect bottom is only set once during layout
        window.rectWork.setTop(window.cursorPosition.y);
        window.rectWork.setLeft(column.workMinX);
        window.rectWork.setRight(column.workMaxX);
        window.currentItemWidth = column.itemWidth;

        window.skipItems = column.isSkipItems;
        if (column.isSkipItems) {
            context.lastItemData.id = 0;
            context.lastItemData.statusFlags = ItemStatusFlags.NONE;
        }

        // Also see tablePushColumnChannel()
        if ((table.flags & TableFlags.NO_CLIP) != 0) {
            table.drawSplitter.setCurrentChannel(window.drawList, TABLE_DRAW_CHANNEL_NOCLIP);
        } else {
            setWindowClipRectBeforeSetChannel(window, column.clipRect);
            table.drawSplitter.setCurrentChannel(window.drawList, column.drawChannelCurrent);
        }

        // Logging
        if (context.logEnabled && !column.isSkipItems) {
            IkGuiImplLogging.logRenderedText(window.cursorPosition.y, "|");
            context.logLinePosY = Float.MAX_VALUE;
        }
    }

    /**
     * End a cell, called by tableNextRow()/tableSetColumnIndex()/tableNextColumn().
     *
     * @param table The table.
     */
    static void tableEndCell(@NonNull Table table) {
        final TableColumn column = table.columns[table.currentColumn];
        final Window window = table.innerWindow;

        // Report the maximum position so we can infer the content size per column
        final float maxX = window.cursorMaxPosition.x;
        if ((table.rowFlags & TableRowFlags.HEADERS) != 0) {
            // Useful in case the user submits contents in a header row that is not tableHeader()
            column.contentMaxXHeadersUsed = Math.max(column.contentMaxXHeadersUsed, maxX);
        } else if (table.isUnfrozenRows) {
            column.contentMaxXUnfrozen = Math.max(column.contentMaxXUnfrozen, maxX);
        } else {
            column.contentMaxXFrozen = Math.max(column.contentMaxXFrozen, maxX);
        }
        if (column.isEnabled) {
            table.rowPosY2 =
                    Math.max(table.rowPosY2, window.cursorMaxPosition.y + table.rowCellPaddingY);
        }
        column.itemWidth = window.currentItemWidth;

        // Propagate the text baseline for the entire row. This propagates the text baseline from
        // the last line of the cell instead of the first one.
        table.rowTextBaseline = Math.max(table.rowTextBaseline, window.baseOffsetPreviousLine);
    }

    // ---------------------------------------------------------------------------------------------
    // Column width management
    // ---------------------------------------------------------------------------------------------

    /**
     * The maximum column content width given the current layout. Uses the column minX, so this
     * value differs on a per-column basis.
     *
     * @param table The table.
     * @param columnIndex The column index.
     * @return The maximum width.
     */
    static float tableCalcMaxColumnWidth(@NonNull Table table, int columnIndex) {
        final TableColumn column = table.columns[columnIndex];
        float maxWidth = Float.MAX_VALUE;
        final float minColumnDistance =
                table.minColumnWidth
                        + table.cellPaddingX * 2.0f
                        + table.cellSpacingX1
                        + table.cellSpacingX2;
        if ((table.flags & TableFlags.SCROLL_X) != 0) {
            // Frozen columns can't reach beyond the visible width, otherwise scrolling will break.
            // We use the display order as reordering within a set of frozen columns is possible.
            if (column.displayOrder < table.freezeColumnsRequest) {
                maxWidth =
                        (table.innerClipRect.getRight()
                                        - (table.freezeColumnsRequest - column.displayOrder)
                                                * minColumnDistance)
                                - column.minX;
                maxWidth =
                        maxWidth - table.outerPaddingX - table.cellPaddingX - table.cellSpacingX2;
            }
        } else if ((table.flags & TableFlags.NO_KEEP_COLUMNS_VISIBLE) == 0) {
            // If horizontal scrolling is disabled, we apply a final lossless shrinking of columns
            // in order to make sure they are all visible. Because of this we also know that all of
            // the columns will always fit in the work rect, and therefore in the inner rect.
            maxWidth =
                    table.workRect.getRight()
                            - (table.columnsEnabledCount - column.indexWithinEnabledSet - 1)
                                    * minColumnDistance
                            - column.minX;
            maxWidth -= table.cellSpacingX2;
            maxWidth -= table.cellPaddingX * 2.0f;
            maxWidth -= table.outerPaddingX;
        }
        return maxWidth;
    }

    /**
     * Calculate the automatic width of a column, meant to be stored in the column widthAuto.
     *
     * @param table The table.
     * @param column The column.
     * @return The automatic width.
     */
    static float tableGetColumnWidthAuto(@NonNull Table table, @NonNull TableColumn column) {
        final float contentWidthBody =
                Math.max(column.contentMaxXFrozen, column.contentMaxXUnfrozen) - column.workMinX;
        final float contentWidthHeaders = column.contentMaxXHeadersIdeal - column.workMinX;
        float widthAuto = contentWidthBody;
        if ((column.flags & TableColumnFlags.NO_HEADER_WIDTH) == 0) {
            widthAuto = Math.max(widthAuto, contentWidthHeaders);
        }

        // Non-resizable fixed columns preserve their requested width
        if ((column.flags & TableColumnFlags.WIDTH_FIXED) != 0
                && column.initStretchWeightOrWidth > 0.0f
                && ((table.flags & TableFlags.RESIZABLE) == 0
                        || (column.flags & TableColumnFlags.NO_RESIZE) != 0)) {
            widthAuto = column.initStretchWeightOrWidth;
        }

        return Math.max(widthAuto, table.minColumnWidth);
    }

    /**
     * Set the width of a column in the current table, before the layout is locked.
     *
     * @param columnIndex The column index.
     * @param width The inner column width, without padding.
     */
    static void tableSetColumnWidth(int columnIndex, float width) {
        final Table table = context.currentTable;
        if (table == null || table.isLayoutLocked) {
            IkGuiImplDebugTools.reportError(
                    log,
                    "tableSetColumnWidth() needs to be called in a table before the first row");
            return;
        }
        if (columnIndex < 0 || columnIndex >= table.columnsCount) {
            IkGuiImplDebugTools.reportError(
                    log, "tableSetColumnWidth() invalid column index {}", columnIndex);
            return;
        }
        final TableColumn column0 = table.columns[columnIndex];
        float column0Width = width;

        // Apply constraints early. Compare both the requested and actual given width to avoid
        // overwriting the requested width when the column is stuck (minimum size, bounded).
        final float minWidth = table.minColumnWidth;
        final float maxWidth = Math.max(minWidth, column0.widthMax);
        column0Width = MathUtil.clamp(column0Width, minWidth, maxWidth);
        if (column0.widthGiven == column0Width || column0.widthRequest == column0Width) {
            return;
        }

        TableColumn column1 =
                column0.nextEnabledColumn != -1 ? table.columns[column0.nextEnabledColumn] : null;

        // This is surprisingly not simple because of how we support mixing fixed and multiple
        // stretch columns. See the comments in TableSetColumnWidth() in Dear ImGui for the
        // scenarios.

        // [Resize Rule 1] Can't resize from the right of the right-most visible column if there
        // is any stretch column. Implemented in tableUpdateLayout().

        // If we have all fixed columns OR are resizing a fixed column that doesn't come after a
        // stretch one, we can do an offsetting resize. This is the preferred resize path.
        if ((column0.flags & TableColumnFlags.WIDTH_FIXED) != 0
                && (column1 == null
                        || table.leftMostStretchedColumn == -1
                        || table.columns[table.leftMostStretchedColumn].displayOrder
                                >= column0.displayOrder)) {
            column0.widthRequest = column0Width;
            table.isSettingsDirty = true;
            return;
        }

        // We can also use the previous column if there's no next one (this is used when doing an
        // auto-fit on the right-most stretch column)
        if (column1 == null) {
            column1 =
                    column0.prevEnabledColumn != -1
                            ? table.columns[column0.prevEnabledColumn]
                            : null;
        }
        if (column1 == null) {
            return;
        }

        // Resizing from the right side of a stretch column before a fixed column forwards sizing
        // to the left side of the fixed column
        final float column1Width =
                Math.max(column1.widthRequest - (column0Width - column0.widthRequest), minWidth);
        column0Width = column0.widthRequest + column1.widthRequest - column1Width;
        column0.widthRequest = column0Width;
        column1.widthRequest = column1Width;
        if (((column0.flags | column1.flags) & TableColumnFlags.WIDTH_STRETCH) != 0) {
            tableUpdateColumnsWeightFromWidth(table);
        }
        table.isSettingsDirty = true;
    }

    /**
     * Disable clipping then auto-fit a single column, which takes 2 frames.
     *
     * @param table The table.
     * @param columnIndex The column index.
     */
    static void tableSetColumnWidthAutoSingle(@NonNull Table table, int columnIndex) {
        final TableColumn column = table.columns[columnIndex];
        if (!column.isEnabled) {
            return;
        }
        column.cannotSkipItemsQueue = 1;
        table.autoFitSingleColumn = columnIndex;
    }

    /**
     * Auto-fit all columns.
     *
     * @param table The table.
     */
    static void tableSetColumnWidthAutoAll(@NonNull Table table) {
        for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
            final TableColumn column = table.columns[columnIndex];
            // Can't reset the weight of hidden stretch columns
            if (!column.isEnabled && (column.flags & TableColumnFlags.WIDTH_STRETCH) == 0) {
                continue;
            }
            column.cannotSkipItemsQueue = 1;
            column.autoFitQueue = 1 << 1;
        }
    }

    /**
     * Recalculate the weights of stretch columns from their requested widths.
     *
     * @param table The table.
     */
    static void tableUpdateColumnsWeightFromWidth(@NonNull Table table) {
        if (table.leftMostStretchedColumn == -1 || table.rightMostStretchedColumn == -1) {
            IkGuiImplDebugTools.reportError(
                    log, "tableUpdateColumnsWeightFromWidth() requires stretched columns");
            return;
        }

        // Measure existing quantities
        float visibleWeight = 0.0f;
        float visibleWidth = 0.0f;
        for (TableColumn column : table.columns) {
            if (!column.isEnabled || (column.flags & TableColumnFlags.WIDTH_STRETCH) == 0) {
                continue;
            }
            visibleWeight += column.stretchWeight;
            visibleWidth += column.widthRequest;
        }
        if (visibleWeight <= 0.0f || visibleWidth <= 0.0f) {
            return;
        }

        // Apply new weights
        for (TableColumn column : table.columns) {
            if (!column.isEnabled || (column.flags & TableColumnFlags.WIDTH_STRETCH) == 0) {
                continue;
            }
            column.stretchWeight = (column.widthRequest / visibleWidth) * visibleWeight;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------------

    /**
     * Set the window clip rect, and the draw list clip rect, before changing draw channels.
     *
     * @param window The window.
     * @param clipRect The new clip rect.
     */
    static void setWindowClipRectBeforeSetChannel(
            @NonNull Window window, @NonNull RectFloat clipRect) {
        window.rectCurrentClip.set(clipRect);
        window.drawList.setCurrentClipRect(
                clipRect.getLeft(), clipRect.getTop(), clipRect.getRight(), clipRect.getBottom());
    }

    /**
     * Switch to the background channel, used by selectable() and other widgets to draw across
     * columns in the background.
     */
    static void tablePushBackgroundChannel() {
        final Window window = context.windowCurrent;
        final Table table = context.currentTable;

        table.hostBackupInnerClipRect.set(window.rectCurrentClip);
        setWindowClipRectBeforeSetChannel(window, table.background2ClipRectForDrawCommand);
        table.drawSplitter.setCurrentChannel(window.drawList, table.background2DrawChannelCurrent);
    }

    /** Switch back from the background channel to the current column channel. */
    static void tablePopBackgroundChannel() {
        final Window window = context.windowCurrent;
        final Table table = context.currentTable;

        setWindowClipRectBeforeSetChannel(window, table.hostBackupInnerClipRect);
        if (table.currentColumn >= 0) {
            table.drawSplitter.setCurrentChannel(
                    window.drawList, table.columns[table.currentColumn].drawChannelCurrent);
        }
    }

    /**
     * Switch to the draw channel of a column, also see tableBeginCell().
     *
     * @param columnIndex The column index.
     */
    static void tablePushColumnChannel(int columnIndex) {
        final Table table = context.currentTable;
        if ((table.flags & TableFlags.NO_CLIP) != 0) {
            return;
        }
        final Window window = context.windowCurrent;
        final TableColumn column = table.columns[columnIndex];
        setWindowClipRectBeforeSetChannel(window, column.clipRect);
        table.drawSplitter.setCurrentChannel(window.drawList, column.drawChannelCurrent);
    }

    /** Switch back to the draw channel of the current column. */
    static void tablePopColumnChannel() {
        final Table table = context.currentTable;
        // Calling treePop() after tableNextRow() is supported
        if ((table.flags & TableFlags.NO_CLIP) != 0 || table.currentColumn == -1) {
            return;
        }
        final Window window = context.windowCurrent;
        final TableColumn column = table.columns[table.currentColumn];
        setWindowClipRectBeforeSetChannel(window, column.clipRect);
        table.drawSplitter.setCurrentChannel(window.drawList, column.drawChannelCurrent);
    }

    /**
     * Allocate draw channels, called by tableUpdateLayout().
     *
     * <ul>
     *   <li>We allocate them following storage order instead of display order, so reordering
     *       columns won't needlessly increase memory use.
     *   <li>After crossing the frozen rows, all columns see their current draw channel changed to a
     *       second set of channels.
     *   <li>We only use the dummy draw channel so we can push a null clipping rectangle into it
     *       without affecting other channels.
     *   <li>We allocate 1 or 2 background draw channels, because the background channel is only
     *       used for horizontal spanning.
     * </ul>
     *
     * @param table The table.
     */
    static void tableSetupDrawChannels(@NonNull Table table) {
        final int freezeRowMultiplier = table.freezeRowsCount > 0 ? 2 : 1;
        final int channelsForRow =
                (table.flags & TableFlags.NO_CLIP) != 0 ? 1 : table.columnsEnabledCount;
        final int channelsForBackground = 1 + freezeRowMultiplier;
        final int channelsForDummy =
                table.columnsEnabledCount < table.columnsCount
                                || !table.visibleMaskByIndex.equals(table.enabledMaskByIndex)
                        ? 1
                        : 0;
        final int channelsTotal =
                channelsForBackground + channelsForRow * freezeRowMultiplier + channelsForDummy;
        table.drawSplitter.split(table.innerWindow.drawList, channelsTotal);
        table.dummyDrawChannel = channelsForDummy > 0 ? channelsTotal - 1 : -1;
        table.background2DrawChannelCurrent = TABLE_DRAW_CHANNEL_BG2_FROZEN;
        table.background2DrawChannelUnfrozen =
                table.freezeRowsCount > 0 ? 2 + channelsForRow : TABLE_DRAW_CHANNEL_BG2_FROZEN;

        int drawChannelCurrent = 2;
        for (TableColumn column : table.columns) {
            if (column.isVisibleX && column.isVisibleY) {
                column.drawChannelFrozen = drawChannelCurrent;
                column.drawChannelUnfrozen =
                        drawChannelCurrent + (table.freezeRowsCount > 0 ? channelsForRow + 1 : 0);
                if ((table.flags & TableFlags.NO_CLIP) == 0) {
                    drawChannelCurrent++;
                }
            } else {
                column.drawChannelFrozen = table.dummyDrawChannel;
                column.drawChannelUnfrozen = table.dummyDrawChannel;
            }
            column.drawChannelCurrent = column.drawChannelFrozen;
        }

        // Cell highlights are clipped with the background clip rect. When unfreezing it will be
        // made smaller to fit the scrolling rect.
        table.backgroundClipRect.set(table.innerClipRect);
        table.background0ClipRectForDrawCommand.set(table.outerWindow.rectCurrentClip);
        table.background2ClipRectForDrawCommand.set(table.hostClipRect);
    }

    /**
     * The border color for a column.
     *
     * @param table The table.
     * @param order The display order of the column.
     * @param columnIndex The column index.
     * @return The color.
     */
    static int tableGetColumnBorderColor(@NonNull Table table, int order, int columnIndex) {
        final boolean isHovered = table.hoveredColumnBorder == columnIndex;
        final boolean isResized =
                table.resizedColumn == columnIndex
                        && table.instanceInteracted == table.instanceCurrent;
        final boolean isFrozenSeparator = table.freezeColumnsCount == order + 1;
        if (isResized || isHovered) {
            return IkGuiImplUtils.getColorWithGlobalAlpha(
                    isResized ? ColorType.SEPARATOR_ACTIVE : ColorType.SEPARATOR_HOVERED);
        }
        if (isFrozenSeparator
                || (table.flags
                                & (TableFlags.NO_BORDERS_IN_BODY
                                        | TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE))
                        != 0) {
            return table.borderColorStrong;
        }
        return table.borderColorLight;
    }

    /**
     * Draw the table borders.
     *
     * @param table The table.
     */
    static void tableDrawBorders(@NonNull Table table) {
        final Window innerWindow = table.innerWindow;
        if (!table.outerWindow.rectCurrentClip.overlaps(table.outerRect)) {
            return;
        }

        final DrawList innerDrawList = innerWindow.drawList;
        table.drawSplitter.setCurrentChannel(innerDrawList, TABLE_DRAW_CHANNEL_BG0);
        final RectFloat clip = table.background0ClipRectForDrawCommand;
        innerDrawList.pushClipRect(
                clip.getLeft(), clip.getTop(), clip.getRight(), clip.getBottom(), false);

        // Draw the inner border and resizing feedback
        final TableInstanceData tableInstance = table.getInstanceData(table.instanceCurrent);
        final float borderSize = TABLE_BORDER_SIZE;
        final float headerTop =
                table.freezeRowsCount >= 1 ? table.innerRect.getTop() : table.workRect.getTop();
        final float drawY1 =
                Math.max(table.innerRect.getTop(), headerTop + table.angledHeadersHeight)
                        + ((table.flags & TableFlags.BORDERS_OUTER_H) != 0 ? 1.0f : 0.0f);
        final float drawY2Body = table.innerRect.getBottom();
        final float drawY2Head =
                table.isUsingHeaders
                        ? Math.min(
                                table.innerRect.getBottom(),
                                headerTop + tableInstance.lastTopHeadersRowHeight)
                        : drawY1;
        if ((table.flags & TableFlags.BORDERS_INNER_V) != 0) {
            for (int order = 0; order < table.columnsCount; ++order) {
                if (!table.enabledMaskByDisplayOrder.get(order)) {
                    continue;
                }

                final int columnIndex = table.displayOrderToIndex[order];
                final TableColumn column = table.columns[columnIndex];
                final boolean isHovered = table.hoveredColumnBorder == columnIndex;
                final boolean isResized =
                        table.resizedColumn == columnIndex
                                && table.instanceInteracted == table.instanceCurrent;
                final boolean isResizable =
                        (column.flags
                                        & (TableColumnFlags.NO_RESIZE
                                                | TableColumnFlags.INTERNAL_NO_DIRECT_RESIZE))
                                == 0;
                final boolean isFrozenSeparator = table.freezeColumnsCount == order + 1;
                if (column.maxX > table.innerClipRect.getRight() && !isResized) {
                    continue;
                }

                // Decide whether the right-most column is visible
                if (column.nextEnabledColumn == -1
                        && !isResizable
                        && ((table.flags & TableFlags.INTERNAL_SIZING_MASK)
                                        != TableFlags.SIZING_FIXED_SAME
                                || (table.flags & TableFlags.NO_HOST_EXTEND_X) != 0)) {
                    continue;
                }
                // Assumes a border size of 1
                if (column.maxX <= column.clipRect.getLeft()) {
                    continue;
                }

                // Draw in the outer window so the right-most column won't be clipped
                float drawY2 = drawY2Head;
                if (isFrozenSeparator) {
                    drawY2 = drawY2Body;
                } else if ((table.flags & TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE) != 0
                        && (isHovered || isResized)) {
                    drawY2 = drawY2Body;
                } else if ((table.flags
                                & (TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE
                                        | TableFlags.NO_BORDERS_IN_BODY))
                        == 0) {
                    drawY2 = drawY2Body;
                }
                if (drawY2 > drawY1) {
                    innerDrawList.addLineV(
                            column.maxX,
                            drawY1,
                            drawY2,
                            tableGetColumnBorderColor(table, order, columnIndex),
                            borderSize);
                }
            }
        }

        // Draw the outer border. We offset it by 1, which is a simple way to display it without
        // having it rendered behind cells or not reaching over scrollbars.
        if ((table.flags & TableFlags.BORDERS_OUTER) != 0) {
            final RectFloat outerBorder = table.outerRect;
            final int outerColor = table.borderColorStrong;
            if ((table.flags & TableFlags.BORDERS_OUTER) == TableFlags.BORDERS_OUTER) {
                innerDrawList.addRect(
                        outerBorder.getLeft(),
                        outerBorder.getTop(),
                        outerBorder.getRight(),
                        outerBorder.getBottom(),
                        outerColor,
                        0.0f,
                        DrawFlags.ROUND_CORNERS_ALL,
                        borderSize);
            } else if ((table.flags & TableFlags.BORDERS_OUTER_V) != 0) {
                innerDrawList.addLineV(
                        outerBorder.getLeft(),
                        outerBorder.getTop(),
                        outerBorder.getBottom(),
                        outerColor,
                        borderSize);
                innerDrawList.addLineV(
                        outerBorder.getRight() - borderSize,
                        outerBorder.getTop(),
                        outerBorder.getBottom(),
                        outerColor,
                        borderSize);
            } else if ((table.flags & TableFlags.BORDERS_OUTER_H) != 0) {
                innerDrawList.addLineH(
                        outerBorder.getLeft(),
                        outerBorder.getRight(),
                        outerBorder.getTop(),
                        outerColor,
                        borderSize);
                innerDrawList.addLineH(
                        outerBorder.getLeft(),
                        outerBorder.getRight(),
                        outerBorder.getBottom() - borderSize,
                        outerColor,
                        borderSize);
            }
        }
        if ((table.flags & TableFlags.BORDERS_INNER_H) != 0
                && table.rowPosY2 < table.outerRect.getBottom()) {
            // Draw the bottom-most row border between it and the outer border
            final float borderY = table.rowPosY2;
            if (borderY >= table.backgroundClipRect.getTop()
                    && borderY < table.backgroundClipRect.getBottom()) {
                innerDrawList.addLineH(
                        table.borderX1,
                        table.borderX2,
                        borderY,
                        table.borderColorLight,
                        borderSize);
            }
        }

        innerDrawList.popClipRect();
    }

    // ---------------------------------------------------------------------------------------------
    // Sorting
    // ---------------------------------------------------------------------------------------------

    /**
     * Fetch the latest sort specs for the current table. When specsDirty is true you should sort
     * your data, then clear specsDirty. Don't hold on to this over multiple frames or past a
     * subsequent call to beginTable().
     *
     * @return The sort specs, or null if the table is not sortable.
     */
    public static TableSortSpecs tableGetSortSpecs() {
        final Table table = context.currentTable;
        if (table == null || (table.flags & TableFlags.SORTABLE) == 0) {
            return null;
        }

        // Require layout (in case tableHeadersRow() hasn't been called) as it may alter the sort
        // specs dirty flag in some paths
        if (!table.isLayoutLocked) {
            tableUpdateLayout(table);
        }

        tableSortSpecsBuild(table);
        return table.sortSpecs;
    }

    /**
     * Get one of the available sort directions for a column.
     *
     * @param column The column.
     * @param n The index into the available directions.
     * @return The sort direction.
     */
    private static SortDirection tableGetColumnAvailableSortDirection(
            @NonNull TableColumn column, int n) {
        return SortDirection.values()[(column.sortDirectionsAvailableList >> (n << 1)) & 0x03];
    }

    /**
     * Fix the sort direction if it is currently set to a value which is unavailable (e.g. after
     * activating NO_SORT_ASCENDING/NO_SORT_DESCENDING).
     *
     * @param table The table.
     * @param column The column.
     */
    static void tableFixColumnSortDirection(@NonNull Table table, @NonNull TableColumn column) {
        if (column.sortOrder == -1
                || (column.sortDirectionsAvailableMask & (1 << column.sortDirection.ordinal()))
                        != 0) {
            return;
        }
        column.sortDirection = tableGetColumnAvailableSortDirection(column, 0);
        table.isSortSpecsDirty = true;
    }

    /**
     * Calculate the next sort direction that would be set after clicking the column. If the
     * PREFER_SORT_DESCENDING flag is set, we default to descending on the first click.
     *
     * @param column The column.
     * @return The next sort direction.
     */
    static SortDirection tableGetColumnNextSortDirection(@NonNull TableColumn column) {
        if (column.sortDirectionsAvailableCount <= 0) {
            return SortDirection.NONE;
        }
        if (column.sortOrder == -1) {
            return tableGetColumnAvailableSortDirection(column, 0);
        }
        for (int n = 0; n < 3; ++n) {
            if (column.sortDirection == tableGetColumnAvailableSortDirection(column, n)) {
                return tableGetColumnAvailableSortDirection(
                        column, (n + 1) % column.sortDirectionsAvailableCount);
            }
        }
        return SortDirection.NONE;
    }

    /**
     * Set the sort direction of a column in the current table. The NO_SORT_ASCENDING /
     * NO_SORT_DESCENDING flags are processed in tableSortSpecsSanitize(), which may change or
     * revert the sort direction.
     *
     * @param columnIndex The column index.
     * @param sortDirection The sort direction.
     * @param appendToSortSpecs Whether to add to the existing sort specs (with SORT_MULTI), or
     *     replace them.
     */
    static void tableSetColumnSortDirection(
            int columnIndex, @NonNull SortDirection sortDirection, boolean appendToSortSpecs) {
        final Table table = context.currentTable;

        if ((table.flags & TableFlags.SORT_MULTI) == 0) {
            appendToSortSpecs = false;
        }
        if ((table.flags & TableFlags.SORT_TRISTATE) == 0 && sortDirection == SortDirection.NONE) {
            IkGuiImplDebugTools.reportError(
                    log, "Only tri-state sorting tables can set no sort direction");
            return;
        }

        int sortOrderMax = 0;
        if (appendToSortSpecs) {
            for (TableColumn other : table.columns) {
                sortOrderMax = Math.max(sortOrderMax, other.sortOrder);
            }
        }

        final TableColumn column = table.columns[columnIndex];
        column.sortDirection = sortDirection;
        if (column.sortDirection == SortDirection.NONE) {
            column.sortOrder = -1;
        } else if (column.sortOrder == -1 || !appendToSortSpecs) {
            column.sortOrder = appendToSortSpecs ? sortOrderMax + 1 : 0;
        }

        for (TableColumn other : table.columns) {
            if (other != column && !appendToSortSpecs) {
                other.sortOrder = -1;
            }
            tableFixColumnSortDirection(table, other);
        }
        table.isSettingsDirty = true;
        table.isSortSpecsDirty = true;
    }

    /**
     * Clear sort orders from hidden columns, and make sure there are no gaps or duplicates.
     *
     * @param table The table.
     */
    static void tableSortSpecsSanitize(@NonNull Table table) {
        // Clear the sort order from hidden columns and verify that there are no gaps or duplicates
        int sortOrderCount = 0;
        long sortOrderMask = 0;
        for (TableColumn column : table.columns) {
            if (column.sortOrder != -1 && !column.isEnabled) {
                column.sortOrder = -1;
            }
            if (column.sortOrder == -1) {
                continue;
            }
            sortOrderCount++;
            if (column.sortOrder < 64) {
                sortOrderMask |= 1L << column.sortOrder;
            }
        }

        final boolean needFixLinearize =
                sortOrderCount >= 64 || (1L << sortOrderCount) != sortOrderMask + 1;
        final boolean needFixSingleSortOrder =
                sortOrderCount > 1 && (table.flags & TableFlags.SORT_MULTI) == 0;
        if (needFixLinearize || needFixSingleSortOrder) {
            final boolean[] fixed = new boolean[table.columnsCount];
            for (int sortN = 0; sortN < sortOrderCount; ++sortN) {
                // Rewrite sort order fields if needed so they have no gaps or duplicates
                int columnWithSmallestSortOrder = -1;
                for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
                    if (!fixed[columnIndex]
                            && table.columns[columnIndex].sortOrder != -1
                            && (columnWithSmallestSortOrder == -1
                                    || table.columns[columnIndex].sortOrder
                                            < table.columns[columnWithSmallestSortOrder]
                                                    .sortOrder)) {
                        columnWithSmallestSortOrder = columnIndex;
                    }
                }
                if (columnWithSmallestSortOrder == -1) {
                    break;
                }
                fixed[columnWithSmallestSortOrder] = true;
                table.columns[columnWithSmallestSortOrder].sortOrder = sortN;

                // Make sure only one column has a sort order if SORT_MULTI is not set
                if (needFixSingleSortOrder) {
                    sortOrderCount = 1;
                    for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
                        if (columnIndex != columnWithSmallestSortOrder) {
                            table.columns[columnIndex].sortOrder = -1;
                        }
                    }
                    break;
                }
            }
        }

        // Fallback default sort order (if no column has the DEFAULT_SORT flag)
        if (sortOrderCount == 0 && (table.flags & TableFlags.SORT_TRISTATE) == 0) {
            for (TableColumn column : table.columns) {
                if (column.isEnabled && (column.flags & TableColumnFlags.NO_SORT) == 0) {
                    sortOrderCount = 1;
                    column.sortOrder = 0;
                    column.sortDirection = tableGetColumnAvailableSortDirection(column, 0);
                    break;
                }
            }
        }

        table.sortSpecsCount = sortOrderCount;
    }

    /**
     * Build the public sort specs from the column sort data.
     *
     * @param table The table.
     */
    static void tableSortSpecsBuild(@NonNull Table table) {
        final boolean dirty = table.isSortSpecsDirty;
        final TableSortSpecs sortSpecs = table.sortSpecs;
        if (dirty) {
            tableSortSpecsSanitize(table);
            if (sortSpecs.specs.length != table.sortSpecsCount) {
                sortSpecs.specs = new TableColumnSortSpecs[table.sortSpecsCount];
                for (int n = 0; n < sortSpecs.specs.length; ++n) {
                    sortSpecs.specs[n] = new TableColumnSortSpecs();
                }
            }
            // Mark as dirty for the user
            sortSpecs.specsDirty = true;
            // Mark as not dirty for us
            table.isSortSpecsDirty = false;

            // Write output
            for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
                final TableColumn column = table.columns[columnIndex];
                if (column.sortOrder == -1 || column.sortOrder >= sortSpecs.specs.length) {
                    continue;
                }
                final TableColumnSortSpecs spec = sortSpecs.specs[column.sortOrder];
                spec.columnUserID = column.userData;
                spec.columnIndex = columnIndex;
                spec.sortOrder = column.sortOrder;
                spec.sortDirection = column.sortDirection;
            }
        }
        sortSpecs.specsCount = table.sortSpecsCount;
    }

    /**
     * Fix invalid display order data: compact values (0,1,3 to 0,1,2), preserve relative order
     * (0,3,1 to 0,2,1), and deduplicate (0,4,1,1 to 0,3,1,2).
     *
     * @param table The table.
     */
    static void tableFixDisplayOrder(@NonNull Table table) {
        // Non-reorderable tables always use the default order
        if ((table.flags & TableFlags.REORDERABLE) == 0) {
            for (int n = 0; n < table.columnsCount; ++n) {
                table.columns[n].displayOrder = n;
                table.displayOrderToIndex[n] = n;
            }
            return;
        }
        final Integer[] indices = new Integer[table.columnsCount];
        for (int n = 0; n < table.columnsCount; ++n) {
            indices[n] = n;
        }
        // Sort by display order and then index, with -1 sorting after everything else
        Arrays.sort(
                indices,
                (lhs, rhs) -> {
                    final int lhsOrder = table.columns[lhs].displayOrder & 0xFFFF;
                    final int rhsOrder = table.columns[rhs].displayOrder & 0xFFFF;
                    if (lhsOrder != rhsOrder) {
                        return Integer.compare(lhsOrder, rhsOrder);
                    }
                    return Integer.compare(lhs, rhs);
                });
        for (int n = 0; n < table.columnsCount; ++n) {
            table.columns[indices[n]].displayOrder = n;
        }
        for (int n = 0; n < table.columnsCount; ++n) {
            table.displayOrderToIndex[table.columns[n].displayOrder] = n;
        }
    }

    /** Clear per-frame transient table data, called at the start of each frame. */
    static void tablesNewFrame() {
        for (TableTempData tempData : context.tablesTempData) {
            // Unusual: cleared every frame because this is rarely used
            tempData.reconcileColumnsRequests.clear();
        }
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplTables() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
