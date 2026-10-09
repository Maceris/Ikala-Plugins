package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

/** Table headers, angled headers, and the table context menu. */
@Slf4j
class IkGuiImplTableHeaders {
    private static final String TABLE_SIZE_ONE = "Size column to fit###SizeOne";
    private static final String TABLE_SIZE_ALL_FIT = "Size all columns to fit###SizeAll";
    private static final String TABLE_SIZE_ALL_DEFAULT = "Size all columns to default###SizeAll";
    private static final String TABLE_RESET = "Reset";
    private static final String TABLE_RESET_ORDER = "Reset order###ResetOrder";
    private static final String TABLE_RESET_VISIBILITY = "Reset visibility###ResetVisibility";

    static Context context;

    // ---------------------------------------------------------------------------------------------
    // Headers
    // ---------------------------------------------------------------------------------------------

    /**
     * Calculate the header row height, for the unlikely case that some labels are taller than
     * others.
     *
     * @return The header row height.
     */
    static float tableGetHeaderRowHeight() {
        final Table table = context.currentTable;
        float rowHeight = IkGuiInternal.getFontSize();
        final Vector2f size = new Vector2f();
        for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
            if (table.enabledMaskByIndex.get(columnIndex)
                    && (table.columns[columnIndex].flags & TableColumnFlags.NO_HEADER_LABEL) == 0) {
                IkGuiImplUtils.calcTextSize(
                        size, IkGuiImplTables.tableGetColumnName(table, columnIndex), false, -1.0f);
                rowHeight = Math.max(rowHeight, size.y);
            }
        }
        return rowHeight + context.style.variable.cellPadding.y * 2.0f;
    }

    /**
     * Calculate the maximum label width for angled headers.
     *
     * @return The maximum width.
     */
    static float tableGetHeaderAngledMaxLabelWidth() {
        final Table table = context.currentTable;
        float width = 0.0f;
        final Vector2f size = new Vector2f();
        for (int columnIndex = 0; columnIndex < table.columnsCount; ++columnIndex) {
            if (table.enabledMaskByIndex.get(columnIndex)
                    && (table.columns[columnIndex].flags & TableColumnFlags.ANGLED_HEADER) != 0) {
                IkGuiImplUtils.calcTextSize(
                        size, IkGuiImplTables.tableGetColumnName(table, columnIndex), true, -1.0f);
                width = Math.max(width, size.x);
            }
        }
        // Swapped padding
        return width + context.style.variable.cellPadding.y * 2.0f;
    }

    /**
     * Submit a row with header cells, based on the data provided to tableSetupColumn(). This also
     * submits the context menu.
     */
    public static void tableHeadersRow() {
        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableHeadersRow() should only be called inside beginTable()!");
            return;
        }

        // Call layout if not already done. This is automatically done by tableNextRow(), we do it
        // here only to make debugging easier.
        if (!table.isLayoutLocked) {
            IkGuiImplTables.tableUpdateLayout(table);
        }

        // Open the row
        final float rowHeight = tableGetHeaderRowHeight();
        IkGuiImplTables.tableNextRow(TableRowFlags.HEADERS, rowHeight);
        final float rowY1 = context.windowCurrent.cursorPosition.y;
        // Merely an optimization
        if (table.hostSkipItems) {
            return;
        }

        final int columnsCount = IkGuiImplTables.tableGetColumnCount();
        for (int columnIndex = 0; columnIndex < columnsCount; ++columnIndex) {
            if (!IkGuiImplTables.tableSetColumnIndex(columnIndex)
                    && table.lastHeldHeaderColumn != columnIndex) {
                continue;
            }

            // Push an ID to allow empty/unnamed headers. This is also idiomatic as it ensures there
            // is a consistent ID path to access columns.
            final String name =
                    (IkGuiImplTables.tableGetColumnFlags(columnIndex)
                                            & TableColumnFlags.NO_HEADER_LABEL)
                                    != 0
                            ? ""
                            : IkGuiImplTables.tableGetColumnName(columnIndex);
            IkGuiImplUtils.pushID(columnIndex);
            tableHeader(name);
            IkGuiImplUtils.popID();
        }

        // Allow opening the popup from the right-most section after the last column
        final Vector2f mousePos = context.io.mousePosition;
        if (IkGuiImplUtils.isMouseReleased(MouseButton.RIGHT)
                && IkGuiImplTables.tableGetHoveredColumn() == columnsCount
                && mousePos.y >= rowY1
                && mousePos.y < rowY1 + rowHeight) {
            // Will open a non-column-specific popup
            tableOpenContextMenu(columnsCount);
        }
    }

    /**
     * Submit one header cell manually (rarely used). Shows the label, and an optional sort order
     * arrow. Because of how we clip and display sorting indicators, you can't use sameLine() after
     * a tableHeader().
     *
     * @param label The label, which is also used for the ID.
     */
    public static void tableHeader(String label) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return;
        }

        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableHeader() should only be called inside beginTable()!");
            return;
        }
        if (table.currentColumn == -1) {
            IkGuiImplDebugTools.reportError(log, "tableHeader() needs to be called in a column");
            return;
        }
        final int columnIndex = table.currentColumn;
        final TableColumn column = table.columns[columnIndex];
        final StyleVariables style = context.style.variable;

        // Label
        if (label == null) {
            label = "";
        }
        final String displayedLabel = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayedLabel, false, -1.0f);
        final float labelPosX = window.cursorPosition.x;
        final float labelPosY = window.cursorPosition.y;

        // If we already got a row height, use that
        final RectFloat cellRect =
                IkGuiImplTables.tableGetCellBackgroundRect(
                        table, columnIndex, new RectFloat(0, 0, 0, 0));
        final float labelHeight =
                Math.max(labelSize.y, table.rowMinHeight - table.rowCellPaddingY * 2.0f);

        // Calculate the ideal size for the sort order arrow
        float widthArrow = 0.0f;
        float widthSortText = 0.0f;
        boolean sortArrow = false;
        String sortOrderSuffix = "";
        final float arrowScale = 0.65f;
        final float fontSize = IkGuiInternal.getFontSize();
        if ((table.flags & TableFlags.SORTABLE) != 0
                && (column.flags & TableColumnFlags.NO_SORT) == 0) {
            widthArrow = IkGuiInternal.truncate(fontSize * arrowScale + style.framePadding.x);
            if (column.sortOrder != -1) {
                sortArrow = true;
            }
            if (column.sortOrder > 0) {
                sortOrderSuffix = Integer.toString(column.sortOrder + 1);
                final Vector2f suffixSize = new Vector2f();
                IkGuiImplUtils.calcTextSize(suffixSize, sortOrderSuffix, false, -1.0f);
                widthSortText = style.itemInnerSpacing.x + suffixSize.x;
            }
        }

        // We feed our unclipped width to the column without writing to the cursor max position
        final float maxPosX = labelPosX + labelSize.x + widthSortText + widthArrow;
        column.contentMaxXHeadersUsed =
                Math.max(
                        column.contentMaxXHeadersUsed,
                        sortArrow ? cellRect.getRight() : Math.min(maxPosX, cellRect.getRight()));
        column.contentMaxXHeadersIdeal = Math.max(column.contentMaxXHeadersIdeal, maxPosX);

        // Keep the header highlighted when the context menu is open
        final int id = window.getID(label);
        final RectFloat bb =
                new RectFloat(
                        cellRect.getLeft(),
                        cellRect.getTop(),
                        cellRect.getRight(),
                        Math.max(
                                cellRect.getBottom(),
                                cellRect.getTop() + labelHeight + style.cellPadding.y * 2.0f));
        // Don't declare the unclipped width, it'll be fed to contentMaxXHeadersIdeal
        IkGuiInternal.itemSize(0.0f, labelHeight);
        if (!IkGuiInternal.itemAdd(bb, id)) {
            return;
        }

        // Using allow overlap mode because we cover the whole cell, and we want the user to be
        // able to submit subsequent items
        final boolean highlight = table.highlightColumnHeader == columnIndex;
        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        final boolean pressed =
                IkGuiInternal.buttonBehavior(bb, id, hovered, held, ButtonFlags.ALLOW_OVERLAP);
        if (held.get() || hovered.get() || highlight) {
            final int color =
                    IkGuiImplUtils.getColorWithGlobalAlpha(
                            held.get()
                                    ? ColorType.HEADER_ACTIVE
                                    : hovered.get() ? ColorType.HEADER_HOVERED : ColorType.HEADER);
            IkGuiImplTables.tableSetBackgroundColor(
                    TableBackgroundTarget.CELL_BACKGROUND, color, table.currentColumn);
        } else if ((table.rowFlags & TableRowFlags.HEADERS) == 0) {
            // Submit a single cell background color in case we didn't submit a full header row
            IkGuiImplTables.tableSetBackgroundColor(
                    TableBackgroundTarget.CELL_BACKGROUND,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TABLE_HEADER_BACKGROUND),
                    table.currentColumn);
        }
        IkGuiImplNav.renderNavCursor(bb, id, NavRenderCursorFlags.COMPACT, 0.0f);
        if (held.get()) {
            table.heldHeaderColumn = columnIndex;
        }
        window.cursorPosition.y -= style.itemSpacing.y * 0.5f;

        // Drag and drop to reorder columns
        if (held.get()
                && (table.flags & TableFlags.REORDERABLE) != 0
                && IkGuiImplUtils.isMouseDragging(MouseButton.LEFT, -1.0f)
                && !context.dragDropActive) {
            // - While moving a column it will jump to the other side of the mouse, so we also
            //   test the mouse delta.
            // - We need to handle reordering across hidden columns.
            // - Other constraints are enforced by tableQueueSetColumnDisplayOrder().
            table.instanceInteracted = table.instanceCurrent;
            final float mouseX = context.io.mousePosition.x;
            if (context.io.mouseDelta.x < 0.0f
                    && mouseX < cellRect.getLeft()
                    && column.prevEnabledColumn != -1) {
                IkGuiImplTables.tableQueueSetColumnDisplayOrder(
                        table, columnIndex, table.columns[column.prevEnabledColumn].displayOrder);
            }
            if (context.io.mouseDelta.x > 0.0f
                    && mouseX > cellRect.getRight()
                    && column.nextEnabledColumn != -1) {
                IkGuiImplTables.tableQueueSetColumnDisplayOrder(
                        table, columnIndex, table.columns[column.nextEnabledColumn].displayOrder);
            }
        }

        // Sort order arrow. Only clip the label for a visible arrow. Auto-fit still accounts for a
        // possible arrow, but when manually sized smaller we don't clip the label for the sake of
        // an arrow that isn't displayed.
        final float ellipsisMax =
                Math.max(
                        cellRect.getRight() - (sortArrow ? widthArrow + widthSortText : 0.0f),
                        labelPosX);
        if ((table.flags & TableFlags.SORTABLE) != 0
                && (column.flags & TableColumnFlags.NO_SORT) == 0) {
            if (column.sortOrder != -1) {
                float x =
                        Math.max(
                                cellRect.getLeft(),
                                cellRect.getRight() - widthArrow - widthSortText);
                final float y = labelPosY;
                if (column.sortOrder > 0) {
                    IkGuiImplUtils.pushStyleColor(
                            ColorType.TEXT, IkGuiImplUtils.getColor(ColorType.TEXT, 0.70f));
                    IkGuiInternal.renderText(
                            x + style.itemInnerSpacing.x, y, sortOrderSuffix, false);
                    IkGuiImplUtils.popStyleColor();
                    x += widthSortText;
                }
                IkGuiInternal.renderArrow(
                        window.drawList,
                        x,
                        y,
                        IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT),
                        column.sortDirection == SortDirection.ASCENDING
                                ? Direction.UP
                                : Direction.DOWN,
                        arrowScale);
            }

            // Handle clicking on the column header to adjust the sort order
            if (pressed && table.reorderColumn != columnIndex) {
                final SortDirection sortDirection =
                        IkGuiImplTables.tableGetColumnNextSortDirection(column);
                IkGuiImplTables.tableSetColumnSortDirection(
                        columnIndex, sortDirection, context.io.keyShift);
            }
        }

        // Render the clipped label
        IkGuiInternal.renderTextEllipsis(
                labelPosX,
                labelPosY,
                ellipsisMax,
                bb.getBottom(),
                ellipsisMax,
                displayedLabel,
                labelSize);

        final boolean textClipped = labelSize.x > ellipsisMax - labelPosX;
        if (textClipped && hovered.get() && context.activeID == 0) {
            IkGuiImplPopups.setItemTooltip(displayedLabel);
        }

        // We don't use beginPopupContextItem() because we want the popup to stay up even after
        // the column is hidden
        if (IkGuiImplPopups.isPopupOpenRequestForItem(PopupFlags.NONE, id)) {
            tableOpenContextMenu(columnIndex);
        }

        IkGuiInternal.testEngineItemInfo(id, label, context.lastItemData.statusFlags);
    }

    /**
     * Submit a row with angled headers for every column with the TableColumnFlags.ANGLED_HEADER
     * flag. Must be the first row.
     */
    public static void tableAngledHeadersRow() {
        final Table table = context.currentTable;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableAngledHeadersRow() should only be called inside beginTable()!");
            return;
        }
        final TableTempData tempData = table.tempData;
        tempData.angledHeadersRequests.clear();

        // Which column needs highlighting?
        final int rowID = IkGuiImplUtils.getID("##AngledHeaders");
        final TableInstanceData tableInstance = table.getInstanceData(table.instanceCurrent);
        int highlightColumn =
                table.lastHeldHeaderColumn != -1
                        ? table.lastHeldHeaderColumn
                        : table.highlightColumnHeader;
        if (highlightColumn == -1
                && table.hoveredColumnBody != -1
                && tableInstance.hoveredRowLast == 0
                && table.hoveredColumnBorder == -1
                && (context.activeID == 0
                        || context.activeID == rowID
                        || table.isActiveIDInTable
                        || context.dragDropActive)) {
            highlightColumn = table.hoveredColumnBody;
        }

        // Build up the requests
        final int headerBackgroundColor =
                IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TABLE_HEADER_BACKGROUND);
        final int textColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT);
        for (int order = 0; order < table.columnsCount; ++order) {
            if (!table.enabledMaskByDisplayOrder.get(order)) {
                continue;
            }
            final int columnIndex = table.displayOrderToIndex[order];
            final TableColumn column = table.columns[columnIndex];
            if ((column.flags & TableColumnFlags.ANGLED_HEADER) == 0) {
                continue;
            }
            tempData.angledHeadersRequests.add(
                    new TableHeaderData(
                            columnIndex,
                            textColor,
                            headerBackgroundColor,
                            columnIndex == highlightColumn
                                    ? IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.HEADER)
                                    : 0));
        }

        // Render the row
        tableAngledHeadersRowEx(
                rowID,
                (float) Math.toRadians(context.style.variable.tableAngledHeadersAngle),
                0.0f,
                tempData.angledHeadersRequests.toArray(new TableHeaderData[0]));
    }

    /**
     * Submit a row with angled headers. The data must be fed left to right.
     *
     * @param rowID The ID of the row.
     * @param angle The angle in radians, from -pi/2 to pi/2.
     * @param maxLabelWidth The maximum label width, or 0 to calculate it automatically.
     * @param data The headers to display.
     */
    static void tableAngledHeadersRowEx(
            int rowID, float angle, float maxLabelWidth, @NonNull TableHeaderData[] data) {
        final Table table = context.currentTable;
        final Window window = context.windowCurrent;
        final DrawList drawList = window.drawList;
        if (table == null) {
            IkGuiImplDebugTools.reportError(
                    log, "tableAngledHeadersRowEx() should only be called inside beginTable()!");
            return;
        }
        if (table.currentRow != -1) {
            IkGuiImplDebugTools.reportError(log, "Angled headers must be the first row");
            return;
        }
        final StyleVariables style = context.style.variable;
        final float fontSize = IkGuiInternal.getFontSize();

        if (maxLabelWidth == 0.0f) {
            maxLabelWidth = tableGetHeaderAngledMaxLabelWidth();
        }

        // The angle is expressed in (-pi/2 .. +pi/2) as it is easier to think about for users
        final boolean flipLabel = angle < 0.0f;
        angle -= (float) (Math.PI * 0.5);
        final float cosA = (float) Math.cos(angle);
        final float sinA = (float) Math.sin(angle);
        final float labelCosA = flipLabel ? (float) Math.cos(angle + Math.PI) : cosA;
        final float labelSinA = flipLabel ? (float) Math.sin(angle + Math.PI) : sinA;

        // Calculate our base metrics and set the angled headers data before the first call to
        // tableNextRow()
        final float headerHeight = fontSize + style.cellPadding.x * 2.0f;
        final float rotatedY =
                maxLabelWidth * sinA + (flipLabel ? headerHeight : -headerHeight) * cosA;
        final float rowHeight = IkGuiInternal.truncate(Math.abs(rotatedY));
        table.angledHeadersHeight = rowHeight;
        table.angledHeadersSlope = sinA != 0.0f ? cosA / sinA : 0.0f;
        // Vector from bottom-left to top-left, and from bottom-right to top-right
        final float angledVectorX = cosA * (rowHeight / -sinA);
        final float angledVectorY = sinA * (rowHeight / -sinA);

        // Declare the row, override and draw our own background
        IkGuiImplTables.tableNextRow(TableRowFlags.HEADERS, rowHeight);
        IkGuiImplTables.tableNextColumn();
        final RectFloat rowRect =
                new RectFloat(
                        table.workRect.getLeft(),
                        table.backgroundClipRect.getTop(),
                        table.workRect.getRight(),
                        table.rowPosY2);
        table.drawSplitter.setCurrentChannel(drawList, IkGuiImplTables.TABLE_DRAW_CHANNEL_BG0);
        float clipRectMinX = table.backgroundClipRect.getLeft();
        if (table.freezeColumnsCount > 0) {
            clipRectMinX =
                    Math.max(
                            clipRectMinX,
                            table.columns[table.displayOrderToIndex[table.freezeColumnsCount - 1]]
                                    .maxX);
        }
        // Cancel
        IkGuiImplTables.tableSetBackgroundColor(TableBackgroundTarget.ROW_BACKGROUND_0, 0, -1);
        final RectFloat backgroundClip = table.backgroundClipRect;
        // Span all columns
        IkGuiImplLayout.pushClipRect(
                backgroundClip.getLeft(),
                backgroundClip.getTop(),
                backgroundClip.getRight(),
                backgroundClip.getBottom(),
                false);
        drawList.addRectFilled(
                backgroundClip.getLeft(),
                rowRect.getTop(),
                backgroundClip.getRight(),
                rowRect.getBottom(),
                IkGuiImplUtils.getColor(ColorType.TABLE_HEADER_BACKGROUND, 0.25f));
        IkGuiImplLayout.pushClipRect(
                clipRectMinX,
                backgroundClip.getTop(),
                backgroundClip.getRight(),
                backgroundClip.getBottom(),
                true);

        IkGuiInternal.buttonBehavior(rowRect, rowID, null, null, ButtonFlags.NONE);
        IkGuiInternal.keepAliveID(rowID);

        // We don't have the font ascent, so we treat it as the full font size, which needs no
        // offset
        final float lineOffsetForAscentX = 0.0f;
        // We always use swapped padding components
        final Vector2f padding = style.cellPadding;
        final Vector2f align = style.tableAngledHeadersTextAlign;

        // Draw backgrounds and labels in the first pass, then all borders
        float maxX = -Float.MAX_VALUE;
        final float[] shapeX = new float[4];
        final float[] shapeY = new float[4];
        final Vector2f labelSize = new Vector2f();
        for (int pass = 0; pass < 2; ++pass) {
            for (int order = 0; order < data.length; ++order) {
                final TableHeaderData request = data[order];
                final int columnIndex = request.index;
                final TableColumn column = table.columns[columnIndex];

                shapeX[0] = column.maxX;
                shapeY[0] = rowRect.getBottom();
                shapeX[1] = column.minX;
                shapeY[1] = rowRect.getBottom();
                shapeX[2] = shapeX[1] + angledVectorX;
                shapeY[2] = shapeY[1] + angledVectorY;
                shapeX[3] = shapeX[0] + angledVectorX;
                shapeY[3] = shapeY[0] + angledVectorY;
                if (pass == 0) {
                    // Draw the shape
                    drawList.addQuadFilled(
                            shapeX[0],
                            shapeY[0],
                            shapeX[1],
                            shapeY[1],
                            shapeX[2],
                            shapeY[2],
                            shapeX[3],
                            shapeY[3],
                            request.backgroundColor0);
                    // Optional highlight
                    if (request.backgroundColor1 != 0) {
                        drawList.addQuadFilled(
                                shapeX[0],
                                shapeY[0],
                                shapeX[1],
                                shapeY[1],
                                shapeX[2],
                                shapeY[2],
                                shapeX[3],
                                shapeY[3],
                                request.backgroundColor1);
                    }
                    maxX = Math.max(maxX, shapeX[3]);

                    // Draw the label. Each line follows the horizontal border, rather than the
                    // whole block being rotated.
                    final String labelName =
                            Hash.getDisplayedText(
                                    IkGuiImplTables.tableGetColumnName(table, columnIndex));
                    final String[] lines = labelName.split("\n", -1);
                    final float lineOffsetStepX = fontSize / -sinA;
                    final int labelLines = lines.length;

                    // Left/right alignment
                    float lineOffsetCurrentX =
                            flipLabel ? (labelLines - 1) * lineOffsetStepX : 0.0f;
                    final float lineOffsetForAlignX =
                            (float)
                                    Math.floor(
                                            Math.max(
                                                            ((column.maxX - column.minX)
                                                                            - padding.x * 2.0f)
                                                                    - (labelLines
                                                                            * lineOffsetStepX),
                                                            0.0f)
                                                    * align.x);
                    lineOffsetCurrentX += lineOffsetForAlignX - lineOffsetForAscentX;

                    // Register the header width
                    final float headerWidth =
                            column.workMinX
                                    + (float)
                                            Math.ceil(
                                                    labelLines * lineOffsetStepX
                                                            - lineOffsetForAlignX);
                    column.contentMaxXHeadersUsed = headerWidth;
                    column.contentMaxXHeadersIdeal = headerWidth;

                    for (String line : lines) {
                        IkGuiImplUtils.calcTextSize(labelSize, line, false, -1.0f);
                        // Using padding.y * 2 would be symmetrical but hide more text
                        final float clipWidth = maxLabelWidth - padding.y;
                        final float clipHeight =
                                Math.min(
                                        labelSize.y,
                                        column.clipRect.getRight()
                                                - column.workMinX
                                                - lineOffsetCurrentX);
                        // Lay out the label at the window clip rect top left, then transform it
                        final float layoutX = window.rectCurrentClip.getLeft();
                        final float layoutY = window.rectCurrentClip.getTop();

                        // Up/down alignment
                        final float availableSpace =
                                Math.max(
                                        clipWidth
                                                - labelSize.x
                                                + Math.abs(padding.x * cosA) * 2.0f
                                                - Math.abs(padding.y * sinA) * 2.0f,
                                        0.0f);
                        final float verticalOffset =
                                availableSpace * align.y * (flipLabel ? -1.0f : 1.0f);

                        // Rotate and offset the label
                        final float pivotInX = layoutX - verticalOffset;
                        final float pivotInY = layoutY + labelSize.y;
                        float pivotOutX = column.workMinX + cosA * padding.y;
                        float pivotOutY = rowRect.getBottom() + sinA * padding.y;
                        lineOffsetCurrentX += flipLabel ? -lineOffsetStepX : lineOffsetStepX;
                        if (flipLabel) {
                            final float shift = clipWidth - Math.max(0.0f, clipWidth - labelSize.x);
                            pivotOutX += cosA * shift;
                            pivotOutY += sinA * shift;
                        }
                        pivotOutX +=
                                flipLabel
                                        ? lineOffsetCurrentX + lineOffsetStepX
                                        : lineOffsetCurrentX;

                        drawList.pushTextTransform(
                                pivotInX, pivotInY, labelCosA, labelSinA, pivotOutX, pivotOutY);
                        IkGuiImplUtils.pushStyleColor(ColorType.TEXT, request.textColor);
                        IkGuiInternal.renderTextEllipsis(
                                layoutX,
                                layoutY,
                                layoutX + clipWidth,
                                layoutY + clipHeight,
                                layoutX + clipWidth,
                                line,
                                labelSize);
                        IkGuiImplUtils.popStyleColor();
                        drawList.popTextTransform();
                    }
                } else {
                    // Draw the border
                    drawList.addLine(
                            shapeX[0] - IkGuiImplTables.TABLE_BORDER_SIZE * 0.5f,
                            shapeY[0],
                            shapeX[3] - IkGuiImplTables.TABLE_BORDER_SIZE * 0.5f,
                            shapeY[3],
                            IkGuiImplTables.tableGetColumnBorderColor(table, order, columnIndex),
                            IkGuiImplTables.TABLE_BORDER_SIZE);
                }
            }
        }
        IkGuiImplLayout.popClipRect();
        IkGuiImplLayout.popClipRect();
        table.tempData.angledHeadersExtraWidth =
                Math.max(0.0f, maxX - table.columns[table.rightMostEnabledColumn].maxX);
    }

    // ---------------------------------------------------------------------------------------------
    // Context menu
    // ---------------------------------------------------------------------------------------------

    /**
     * Open the table context menu.
     *
     * @param columnIndex The column the menu is for, -1 for no specific column (or the current
     *     column when called within a column).
     */
    static void tableOpenContextMenu(int columnIndex) {
        final Table table = context.currentTable;
        // When called within a column automatically use it, for consistency
        if (columnIndex == -1 && table.currentColumn != -1) {
            columnIndex = table.currentColumn;
        }
        // To facilitate using with tableGetHoveredColumn()
        if (columnIndex == table.columnsCount) {
            columnIndex = -1;
        }
        if (columnIndex < -1 || columnIndex >= table.columnsCount) {
            IkGuiImplDebugTools.reportError(
                    log, "tableOpenContextMenu() invalid column index {}", columnIndex);
            return;
        }
        if ((table.flags & (TableFlags.RESIZABLE | TableFlags.REORDERABLE | TableFlags.HIDEABLE))
                != 0) {
            table.isContextPopupOpen = true;
            table.contextPopupColumn = columnIndex;
            table.instanceInteracted = table.instanceCurrent;
            final int contextMenuID = Hash.getID("##ContextMenu", table.id);
            IkGuiImplPopups.openPopupEx(contextMenuID, PopupFlags.NONE);
        }
    }

    /**
     * Begin the table context menu popup, if it is open.
     *
     * @param table The table.
     * @return True if the popup is open, and endPopup() needs to be called.
     */
    static boolean tableBeginContextMenuPopup(@NonNull Table table) {
        if (!table.isContextPopupOpen || table.instanceCurrent != table.instanceInteracted) {
            return false;
        }
        final int contextMenuID = Hash.getID("##ContextMenu", table.id);
        if (IkGuiImplPopups.beginPopupEx(
                contextMenuID,
                WindowFlags.ALWAYS_AUTO_RESIZE
                        | WindowFlags.NO_TITLE_BAR
                        | WindowFlags.NO_SAVED_SETTINGS)) {
            return true;
        }
        table.isContextPopupOpen = false;
        return false;
    }

    /**
     * A menu item that selects on release, so it can be dragged to reorder columns.
     *
     * @param label The label.
     * @param selected Whether to show a check mark.
     * @param enabled Whether the item is enabled.
     * @return True if the item was activated.
     */
    private static boolean menuItemForColumnReorder(
            @NonNull String label, boolean selected, boolean enabled) {
        final Window window = context.windowCurrent;

        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, label, true, -1.0f);
        final MenuColumns offsets = window.menuColumns;
        final float fontSize = IkGuiInternal.getFontSize();
        final float checkMarkWidth = IkGuiInternal.truncate(fontSize * 1.20f);
        // Feedback for the next frame
        final float minWidth = offsets.declColumns(0.0f, labelSize.x, 0.0f, checkMarkWidth);
        final float stretchWidth =
                Math.max(0.0f, IkGuiImplUtils.getContentRegionAvailableX() - minWidth);
        final float textX = window.cursorPosition.x;
        final float textY = window.cursorPosition.y + window.baseOffsetCurrentLine;

        final int id = window.getID(label);
        int selectableFlags =
                SelectableFlags.INTERNAL_SELECT_ON_RELEASE
                        | SelectableFlags.INTERNAL_SPAN_AVAILABLE_WIDTH;
        if (context.activeID == id) {
            // Stays highlighted while dragging
            selectableFlags |= SelectableFlags.HIGHLIGHT;
        }
        // But disable toggling once moved
        final boolean hasBeenMoved = context.activeID == id && context.activeIDHasBeenEditedBefore;

        // We don't use the disabled selectable flag so that the check mark is also affected
        IkGuiImplUtils.beginDisabled(!enabled);
        // We can't use isMouseDragging() as the button is already released
        final boolean result =
                IkGuiImplMiscWidgets.selectable(
                                label, false, selectableFlags, minWidth, labelSize.y)
                        && !hasBeenMoved;
        if ((context.lastItemData.statusFlags & ItemStatusFlags.VISIBLE) != 0 && selected) {
            IkGuiInternal.renderCheckMark(
                    window.drawList,
                    textX + offsets.offsetMark + stretchWidth + fontSize * 0.40f,
                    textY + fontSize * 0.134f * 0.5f,
                    IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.TEXT),
                    fontSize * 0.866f);
        }
        IkGuiImplUtils.endDisabled();

        IkGuiInternal.testEngineItemInfo(
                context.lastItemData.id,
                label,
                context.lastItemData.statusFlags
                        | ItemStatusFlags.CHECKABLE
                        | (selected ? ItemStatusFlags.CHECKED : 0));
        return result;
    }

    /**
     * Output the default context menu into the current window (generally a popup).
     *
     * @param table The table.
     * @param flagsForSectionToDisplay Which sections to display, using table flags. RESIZABLE
     *     displays sizing menu items, REORDERABLE displays "Reset order", and HIDEABLE displays
     *     column visibility menu items.
     */
    static void tableDrawDefaultContextMenu(@NonNull Table table, int flagsForSectionToDisplay) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return;
        }

        boolean wantSeparator = false;
        final int contextColumnIndex =
                table.contextPopupColumn >= 0 && table.contextPopupColumn < table.columnsCount
                        ? table.contextPopupColumn
                        : -1;
        final TableColumn contextColumn =
                contextColumnIndex != -1 ? table.columns[contextColumnIndex] : null;

        // Sizing
        if ((flagsForSectionToDisplay & TableFlags.RESIZABLE) != 0) {
            if (contextColumn != null) {
                final boolean canResize =
                        (contextColumn.flags & TableColumnFlags.NO_RESIZE) == 0
                                && contextColumn.isEnabled;
                if (IkGuiImplMenus.menuItem(TABLE_SIZE_ONE, null, false, canResize)) {
                    IkGuiImplTables.tableSetColumnWidthAutoSingle(table, contextColumnIndex);
                }
            }

            final String sizeAllDescription;
            if (table.columnsEnabledFixedCount == table.columnsEnabledCount
                    && (table.flags & TableFlags.INTERNAL_SIZING_MASK)
                            != TableFlags.SIZING_FIXED_SAME) {
                sizeAllDescription = TABLE_SIZE_ALL_FIT;
            } else {
                sizeAllDescription = TABLE_SIZE_ALL_DEFAULT;
            }
            if (IkGuiImplMenus.menuItem(sizeAllDescription, null, false, true)) {
                IkGuiImplTables.tableSetColumnWidthAutoAll(table);
            }
            wantSeparator = true;
        }

        // Reset order/visibility etc.
        if ((flagsForSectionToDisplay & (TableFlags.REORDERABLE | TableFlags.HIDEABLE)) != 0
                && IkGuiImplMenus.beginMenu(TABLE_RESET, true)) {
            // Can't be hidden because it would mess with drag reordering
            if ((flagsForSectionToDisplay & TableFlags.REORDERABLE) != 0
                    && IkGuiImplMenus.menuItem(
                            TABLE_RESET_ORDER, null, false, !table.isDefaultDisplayOrder)) {
                table.isResetDisplayOrderRequest = true;
            }
            if ((flagsForSectionToDisplay & TableFlags.HIDEABLE) != 0
                    && IkGuiImplMenus.menuItem(
                            TABLE_RESET_VISIBILITY, null, false, !table.isDefaultVisibility)) {
                table.isResetVisibilityRequest = true;
            }
            IkGuiImplMenus.endMenu();
        }

        // Hiding / visibility
        if ((flagsForSectionToDisplay & TableFlags.HIDEABLE) != 0) {
            if (wantSeparator) {
                IkGuiImplLayout.separator();
            }

            // While reordering, we calculate the min/max allowed range once here. This assumes
            // that reordering constraints output a single range.
            final boolean isReordering =
                    context.activeID != 0
                            && context.activeIDWindow == context.windowCurrent
                            && table.reorderColumn != -1
                            && context.activeIDHasBeenEditedBefore;
            final int reorderSourceOrder =
                    isReordering ? table.columns[table.reorderColumn].displayOrder : -1;
            final int reorderMinOrder =
                    isReordering
                            ? IkGuiImplTables.tableGetMaxDisplayOrderAllowed(
                                    table, reorderSourceOrder, 0)
                            : 0;
            final int reorderMaxOrder =
                    isReordering
                            ? IkGuiImplTables.tableGetMaxDisplayOrderAllowed(
                                    table, reorderSourceOrder, table.columnsCount - 1)
                            : table.columnsCount - 1;
            IkGuiImplUtils.pushItemFlag(ItemFlags.AUTO_CLOSE_POPUPS, false);
            for (int order = 0; order < table.columnsCount; ++order) {
                final int columnIndex = table.displayOrderToIndex[order];
                final TableColumn column = table.columns[columnIndex];
                if ((column.flags & TableColumnFlags.DISABLED) != 0) {
                    continue;
                }

                String name = IkGuiImplTables.tableGetColumnName(table, columnIndex);
                if (name.isEmpty()) {
                    name = "<Unknown>";
                }

                // Make sure we can't hide the last active column
                boolean menuItemEnabled = (column.flags & TableColumnFlags.NO_HIDE) == 0;
                if (column.isUserEnabled && table.columnsEnabledCount <= 1) {
                    menuItemEnabled = false;
                }
                if (isReordering
                        && (column.displayOrder < reorderMinOrder
                                || column.displayOrder > reorderMaxOrder)) {
                    menuItemEnabled = false;
                }
                if (menuItemForColumnReorder(name, column.isUserEnabled, menuItemEnabled)) {
                    column.isUserEnabledNextFrame = !column.isUserEnabled;
                }

                // Drag to reorder. It is currently not possible to reorder columns marked with
                // NO_HIDE.
                if (IkGuiImplUtils.isItemActive()
                        && IkGuiImplUtils.isMouseDragging(MouseButton.LEFT, -1.0f)
                        && context.activeIDSource == GuiInputSource.MOUSE
                        && (table.flags & TableFlags.REORDERABLE) != 0) {
                    // Disable toggling in menuItemForColumnReorder(), and start dimming to display
                    // the allowed reorder targets
                    context.activeIDHasBeenEditedBefore = true;
                    table.reorderColumn = columnIndex;
                    if (!IkGuiImplUtils.isItemHovered(HoveredFlags.NONE)) {
                        final RectFloat rect = context.lastItemData.rect;
                        final float mouseY = context.io.mousePosition.y;
                        final int reorderDirection =
                                mouseY < (rect.getTop() + rect.getBottom()) * 0.5f ? -1 : 1;
                        final float reorderAmount =
                                (reorderDirection < 0
                                                ? rect.getTop() - mouseY
                                                : mouseY - rect.getBottom())
                                        / rect.getHeight();
                        // Estimated target order, will be validated and clamped
                        final int dstOrder =
                                column.displayOrder
                                        + (int) Math.ceil(reorderAmount) * reorderDirection;
                        IkGuiImplTables.tableQueueSetColumnDisplayOrder(
                                table, columnIndex, dstOrder);
                    }
                }
            }
            IkGuiImplUtils.popItemFlag();
        }
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplTableHeaders() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
