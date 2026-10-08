package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.GroupData;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.StyleVariables;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Axis;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.LayoutType;
import com.ikalagaming.graphics.frontend.gui.enums.MouseCursor;
import com.ikalagaming.graphics.frontend.gui.flags.ButtonFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

@Slf4j
class IkGuiImplLayout {
    static Context context;

    /**
     * Lock the horizontal starting position, and capture the group bounding box into one "item" (so
     * you can use isItemHovered() or layout primitives such as sameLine() on the whole group).
     */
    public static void beginGroup() {
        final Window window = context.windowCurrent;

        GroupData groupData = new GroupData();
        groupData.windowID = window.id;
        groupData.backupCursorPos.set(window.cursorPosition);
        groupData.backupCursorPosPreviousLine.set(window.cursorPreviousLinePosition);
        groupData.backupCursorMaxPos.set(window.cursorMaxPosition);
        groupData.backupIndent = window.indent;
        groupData.backupGroupOffset = window.groupOffset;
        groupData.backupCurrentLineSize.set(window.lineSizeCurrent);
        groupData.backupCurrentLineTextBaseOffset = window.baseOffsetCurrentLine;
        groupData.backupActiveIDIsAlive = context.activeIDIsAlive;
        groupData.backupHoveredIDIsAlive = context.hoveredID != 0;
        groupData.backupIsSameLine = window.sameLine;
        groupData.backupAnyIDHasBeenEditedThisFrame = context.anyIDHasBeenEditedThisFrame;
        groupData.backupDeactivatedIDIsAlive = context.deactivatedItemData.isAlive;
        groupData.emitItem = true;
        context.groupStack.push(groupData);

        window.groupOffset = window.cursorPosition.x - window.position.x - window.columnsOffset;
        window.indent = window.groupOffset;
        window.cursorMaxPosition.set(window.cursorPosition);
        window.lineSizeCurrent.set(0.0f, 0.0f);
        if (context.logEnabled) {
            // Enforce a new line
            context.logLinePosY = -Float.MAX_VALUE;
        }
    }

    /** End a group started with beginGroup(). */
    public static void endGroup() {
        final Window window = context.windowCurrent;
        if (context.groupStack.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Mismatched beginGroup()/endGroup() calls");
            return;
        }

        final GroupData groupData = context.groupStack.peek();
        if (groupData.windowID != window.id) {
            IkGuiImplDebugTools.reportError(log, "endGroup() called in the wrong window");
            return;
        }

        // Include the last item rect max as a workaround for things undershooting the cursor max
        // position report
        final RectFloat groupBB =
                new RectFloat(
                        groupData.backupCursorPos.x,
                        groupData.backupCursorPos.y,
                        Math.max(
                                Math.max(
                                        window.cursorMaxPosition.x,
                                        context.lastItemData.rect.getRight()),
                                groupData.backupCursorPos.x),
                        Math.max(
                                Math.max(
                                        window.cursorMaxPosition.y,
                                        context.lastItemData.rect.getBottom()),
                                groupData.backupCursorPos.y));
        window.cursorPosition.set(groupData.backupCursorPos);
        window.cursorPreviousLinePosition.set(groupData.backupCursorPosPreviousLine);
        window.cursorMaxPosition.set(
                Math.max(groupData.backupCursorMaxPos.x, groupBB.getRight()),
                Math.max(groupData.backupCursorMaxPos.y, groupBB.getBottom()));
        window.indent = groupData.backupIndent;
        window.groupOffset = groupData.backupGroupOffset;
        window.lineSizeCurrent.set(groupData.backupCurrentLineSize);
        window.baseOffsetCurrentLine = groupData.backupCurrentLineTextBaseOffset;
        window.sameLine = groupData.backupIsSameLine;
        if (context.logEnabled) {
            // Enforce a new line
            context.logLinePosY = -Float.MAX_VALUE;
        }

        if (!groupData.emitItem) {
            context.groupStack.pop();
            return;
        }

        // FIXME: Incorrect, we should grab the base offset from the first line of the group
        window.baseOffsetCurrentLine =
                Math.max(window.baseOffsetPreviousLine, groupData.backupCurrentLineTextBaseOffset);
        IkGuiInternal.itemSize(groupBB.getWidth(), groupBB.getHeight(), -1.0f);
        IkGuiInternal.itemAdd(groupBB, 0, null, ItemFlags.NO_TAB_STOP);

        // If the current active ID was declared within the boundary of our group, we copy it to
        // the last item ID so isItemActive(), isItemDeactivated() etc. will be functional on the
        // entire group
        final boolean groupContainsCurrentActiveID =
                groupData.backupActiveIDIsAlive != context.activeID
                        && context.activeIDIsAlive == context.activeID
                        && context.activeID != 0;
        final boolean groupContainsDeactivatedID =
                !groupData.backupDeactivatedIDIsAlive && context.deactivatedItemData.isAlive;
        if (groupContainsCurrentActiveID) {
            context.lastItemData.id = context.activeID;
        } else if (groupContainsDeactivatedID) {
            context.lastItemData.id = context.deactivatedItemData.id;
        }
        context.lastItemData.rect.set(groupBB);

        // Forward hovered flag
        final boolean groupContainsCurrentHoveredID =
                !groupData.backupHoveredIDIsAlive && context.hoveredID != 0;
        if (groupContainsCurrentHoveredID) {
            context.lastItemData.statusFlags |= ItemStatusFlags.HOVERED_WINDOW;
        }

        // Forward edited flag
        if (context.anyIDHasBeenEditedThisFrame && !groupData.backupAnyIDHasBeenEditedThisFrame) {
            context.lastItemData.statusFlags |= ItemStatusFlags.EDITED;
        }

        // Forward deactivated flag
        context.lastItemData.statusFlags |= ItemStatusFlags.HAS_DEACTIVATED;
        if (groupContainsDeactivatedID) {
            context.lastItemData.statusFlags |= ItemStatusFlags.DEACTIVATED;
        }

        context.groupStack.pop();
        if (context.debugShowGroupRects) {
            window.drawList.addRect(
                    groupBB.getLeft(),
                    groupBB.getTop(),
                    groupBB.getRight(),
                    groupBB.getBottom(),
                    0xFF00FFFF);
        }
    }

    /**
     * Add an invisible item of the given size, unlike invisibleButton() it won't take the mouse
     * click or be navigable into.
     *
     * @param width The width.
     * @param height The height.
     */
    public static void dummy(float width, float height) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }

        final RectFloat bb =
                new RectFloat(
                        window.cursorPosition.x,
                        window.cursorPosition.y,
                        window.cursorPosition.x + width,
                        window.cursorPosition.y + height);
        IkGuiInternal.itemSize(width, height);
        IkGuiInternal.itemAdd(bb, 0);
    }

    public static float getFrameHeight() {
        return IkGuiInternal.getFontSize() + context.style.variable.framePadding.y * 2.0f;
    }

    public static float getFrameHeightWithSpacing() {
        return IkGuiInternal.getFontSize()
                + context.style.variable.framePadding.y * 2.0f
                + context.style.variable.itemSpacing.y;
    }

    public static float getItemRectHeight() {
        return context.lastItemData.rect.getHeight();
    }

    public static void getItemRectMax(@NonNull Vector2f value) {
        value.set(context.lastItemData.rect.getRight(), context.lastItemData.rect.getBottom());
    }

    public static float getItemRectMaxX() {
        return context.lastItemData.rect.getRight();
    }

    public static float getItemRectMaxY() {
        return context.lastItemData.rect.getBottom();
    }

    public static void getItemRectMin(@NonNull Vector2f value) {
        value.set(context.lastItemData.rect.getLeft(), context.lastItemData.rect.getTop());
    }

    public static float getItemRectMinX() {
        return context.lastItemData.rect.getLeft();
    }

    public static float getItemRectMinY() {
        return context.lastItemData.rect.getTop();
    }

    public static void getItemRectSize(@NonNull Vector2f value) {
        value.set(context.lastItemData.rect.getWidth(), context.lastItemData.rect.getHeight());
    }

    public static float getItemRectWidth() {
        return context.lastItemData.rect.getWidth();
    }

    /**
     * The height of a line of text, which is the font size in pixels.
     *
     * @return The line height.
     */
    public static float getTextLineHeight() {
        float fontScale = (float) context.dpiScaleScreen / context.dpiScaleFont;
        return (int) (fontScale * context.fontSize);
    }

    /**
     * The distance in pixels between two consecutive lines of text.
     *
     * @return The line height plus item spacing.
     */
    public static float getTextLineHeightWithSpacing() {
        return getTextLineHeight() + context.style.variable.itemSpacing.y;
    }

    public static float getTreeNodeToLabelSpacing() {
        return IkGuiInternal.getFontSize() + context.style.variable.framePadding.x * 2.0f;
    }

    /**
     * Move the content position to the right.
     *
     * @param width The amount to indent, or 0 to use the style indent spacing.
     */
    public static void indent(float width) {
        final Window window = IkGuiInternal.getCurrentWindow();
        window.indent += width != 0.0f ? width : context.style.variable.indentSpacing;
        window.cursorPosition.x = window.position.x + window.indent + window.columnsOffset;
    }

    /** Undo a sameLine() or force a new line when in a horizontal-layout context. */
    public static void newLine() {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }

        final LayoutType backupLayoutType = window.layoutType;
        window.layoutType = LayoutType.VERTICAL;
        window.sameLine = false;
        if (window.lineSizeCurrent.y > 0.0f) {
            // In the event that we are on a line with items that is smaller than the font size,
            // we let them dictate the height
            IkGuiInternal.itemSize(0, 0);
        } else {
            IkGuiInternal.itemSize(0, IkGuiInternal.getFontSize());
        }
        window.layoutType = backupLayoutType;
    }

    public static void popClipRect() {
        IkGuiImplWindows.popWindowClipRect(IkGuiInternal.getCurrentWindow());
    }

    public static void popItemWidth() {
        final Window window = context.windowCurrent;
        if (window.itemWidthStack.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Calling popItemWidth() too many times!");
            return;
        }
        window.currentItemWidth = window.itemWidthStack.pop();
    }

    public static void popTextWrapPos() {
        final Window window = context.windowCurrent;
        if (window.textWrapPositionStack.isEmpty()) {
            IkGuiImplDebugTools.reportError(log, "Calling popTextWrapPos() too many times!");
            return;
        }
        window.currentTextWrapPosition = window.textWrapPositionStack.pop();
    }

    public static void pushClipRect(
            float minX, float minY, float maxX, float maxY, boolean intersectWithCurrentClipRect) {
        IkGuiImplWindows.pushWindowClipRect(
                IkGuiInternal.getCurrentWindow(),
                new RectFloat(minX, minY, maxX, maxY),
                intersectWithCurrentClipRect);
    }

    /**
     * Push the width of items for common large "item+label" widgets.
     *
     * @param width Greater than 0 for a width in pixels, 0 to use the default width, less than 0 to
     *     align to the right of the window.
     */
    public static void pushItemWidth(float width) {
        final Window window = context.windowCurrent;
        window.itemWidthStack.push(window.currentItemWidth);
        window.currentItemWidth = width == 0.0f ? window.itemWidthDefault : width;
        context.nextItemData.fieldFlags &=
                ~com.ikalagaming.graphics.frontend.gui.flags.NextItemFlags.HAS_WIDTH;
    }

    /**
     * Push widths for a widget with multiple components, splitting the full width between them.
     *
     * @param components The number of components.
     * @param fullWidth The full width.
     */
    public static void pushMultiItemsWidths(int components, float fullWidth) {
        final Window window = context.windowCurrent;
        if (components <= 0) {
            IkGuiImplDebugTools.reportError(
                    log, "pushMultiItemsWidths() requires at least 1 component");
            return;
        }
        window.itemWidthStack.push(window.currentItemWidth);
        final float itemsWidth =
                fullWidth - context.style.variable.itemInnerSpacing.x * (components - 1);
        float previousSplit = itemsWidth;
        for (int i = components - 1; i > 0; --i) {
            final float nextSplit = IkGuiInternal.truncate(itemsWidth * i / components);
            window.itemWidthStack.push(Math.max(previousSplit - nextSplit, 1.0f));
            previousSplit = nextSplit;
        }
        window.currentItemWidth = Math.max(previousSplit, 1.0f);
        context.nextItemData.fieldFlags &=
                ~com.ikalagaming.graphics.frontend.gui.flags.NextItemFlags.HAS_WIDTH;
    }

    /**
     * Push word-wrapping position for text commands.
     *
     * @param wrapLocalPosX Less than 0 for no wrapping, 0 to wrap to the end of the window (or
     *     column), greater than 0 to wrap at that position in window local space.
     */
    public static void pushTextWrapPos(float wrapLocalPosX) {
        final Window window = IkGuiInternal.getCurrentWindow();
        window.textWrapPositionStack.push(window.currentTextWrapPosition);
        window.currentTextWrapPosition = wrapLocalPosX;
    }

    /**
     * Call between widgets or groups to lay them out horizontally.
     *
     * @param offsetFromStartX If 0, follow right after the previous item. Otherwise, align to the
     *     specified x position (relative to window/group left).
     * @param spacingAfterCurrent If less than 0, use default spacing if offsetFromStartX is 0, and
     *     no spacing otherwise. Greater than or equal to 0 enforces that amount of spacing.
     */
    public static void sameLine(final float offsetFromStartX, float spacingAfterCurrent) {
        final Window window = context.windowCurrent;
        if (window.skipItems) {
            return;
        }

        if (offsetFromStartX != 0.0f) {
            if (spacingAfterCurrent < 0.0f) {
                spacingAfterCurrent = 0.0f;
            }
            window.cursorPosition.set(
                    window.position.x
                            - window.scrollPosition.x
                            + offsetFromStartX
                            + spacingAfterCurrent
                            + window.groupOffset
                            + window.columnsOffset,
                    window.cursorPreviousLinePosition.y);
        } else {
            if (spacingAfterCurrent < 0.0f) {
                spacingAfterCurrent = context.style.variable.itemSpacing.x;
            }
            window.cursorPosition.set(
                    window.cursorPreviousLinePosition.x + spacingAfterCurrent,
                    window.cursorPreviousLinePosition.y);
        }
        window.lineSizeCurrent.set(window.lineSizePrevious);
        window.baseOffsetCurrentLine = window.baseOffsetPreviousLine;
        window.sameLine = true;
    }

    /** Separator, generally horizontal. Inside a menu bar or horizontal layout, it is vertical. */
    public static void separator() {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }
        final float thickness = Math.max(context.style.variable.separatorSize, 1.0f);
        final int color = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.SEPARATOR);

        if (window.layoutType == LayoutType.HORIZONTAL) {
            // Vertical separator, for menu bars (uses the current line height)
            final float x = window.cursorPosition.x;
            final float y1 = window.cursorPosition.y;
            final float y2 = window.cursorPosition.y + window.lineSizeCurrent.y;
            final RectFloat bb = new RectFloat(x, y1, x + thickness, y2);
            IkGuiInternal.itemSize(thickness, 0.0f);
            if (IkGuiInternal.itemAdd(bb, 0)) {
                window.drawList.addRectFilled(
                        bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), color);
                if (context.logEnabled) {
                    IkGuiImplLogging.logText(" |");
                }
            }
            return;
        }

        // Horizontal separator
        final float x1 = window.cursorPosition.x;
        final float x2 = window.rectWork.getRight();
        final RectFloat bb =
                new RectFloat(x1, window.cursorPosition.y, x2, window.cursorPosition.y + thickness);
        IkGuiInternal.itemSize(0.0f, thickness);
        if (IkGuiInternal.itemAdd(bb, 0)) {
            window.drawList.addRectFilled(
                    bb.getLeft(), bb.getTop(), bb.getRight(), bb.getBottom(), color);
            if (context.logEnabled) {
                IkGuiImplLogging.logRenderedText(bb.getTop(), "--------------------------------\n");
            }
        }
    }

    /**
     * Text with a horizontal line on either side, used as a section title.
     *
     * @param label The label, anything after "##" is hidden.
     */
    public static void separatorText(@NonNull String label) {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }
        separatorTextEx(0, label, 0.0f);
    }

    /**
     * Text with a horizontal line on either side. The text is aligned within the separator using
     * the separator text alignment style, and space can be reserved after the text to submit
     * another item with sameLine().
     *
     * @param id The ID of the item, which may be 0.
     * @param label The label, anything after "##" is hidden.
     * @param extraWidth Extra width to reserve after the text.
     */
    static void separatorTextEx(int id, @NonNull String label, float extraWidth) {
        final Window window = context.windowCurrent;
        final StyleVariables style = context.style.variable;

        final String displayed = Hash.getDisplayedText(label);
        final Vector2f labelSize = new Vector2f();
        IkGuiImplUtils.calcTextSize(labelSize, displayed, false, -1.0f);
        final float posX = window.cursorPosition.x;
        final float posY = window.cursorPosition.y;
        final Vector2f padding = style.separatorTextPadding;

        final float separatorThickness = style.separatorTextBorderSize;
        final float minWidth = labelSize.x + extraWidth + padding.x * 2.0f;
        final float minHeight = Math.max(labelSize.y + padding.y * 2.0f, separatorThickness);
        final RectFloat bb =
                new RectFloat(posX, posY, window.rectWork.getRight(), posY + minHeight);
        final float textBaselineY =
                IkGuiInternal.truncate(
                        (bb.getHeight() - labelSize.y) * style.separatorTextAlign.y + 0.999f);
        IkGuiInternal.itemSize(minWidth, minHeight, textBaselineY);
        if (!IkGuiInternal.itemAdd(bb, id)) {
            return;
        }

        final float separator1X1 = posX;
        final float separator2X2 = bb.getRight();
        final float separatorsY =
                IkGuiInternal.truncate((bb.getTop() + bb.getBottom()) * 0.5f + 0.999f);

        final float labelAvailableWidth =
                Math.max(0.0f, separator2X2 - separator1X1 - padding.x * 2.0f);
        final float labelX =
                posX
                        + padding.x
                        + Math.max(
                                0.0f,
                                (labelAvailableWidth - labelSize.x - extraWidth)
                                        * style.separatorTextAlign.x);
        final float labelY = posY + textBaselineY;

        // This allows using sameLine() to position something in the extra width
        window.cursorPreviousLinePosition.x = labelX + labelSize.x;

        final int separatorColor = IkGuiImplUtils.getColorWithGlobalAlpha(ColorType.SEPARATOR);
        if (labelSize.x > 0.0f) {
            final float separator1X2 = labelX - style.itemSpacing.x;
            final float separator2X1 = labelX + labelSize.x + extraWidth + style.itemSpacing.x;
            if (separator1X2 > separator1X1 && separatorThickness > 0.0f) {
                window.drawList.addLineH(
                        separator1X1,
                        separator1X2,
                        separatorsY,
                        separatorColor,
                        separatorThickness);
            }
            if (separator2X2 > separator2X1 && separatorThickness > 0.0f) {
                window.drawList.addLineH(
                        separator2X1,
                        separator2X2,
                        separatorsY,
                        separatorColor,
                        separatorThickness);
            }
            if (context.logEnabled) {
                IkGuiImplLogging.logSetNextTextDecoration("---", null);
            }
            IkGuiInternal.renderTextEllipsis(
                    labelX,
                    labelY,
                    bb.getRight(),
                    bb.getBottom() + style.itemSpacing.y,
                    bb.getRight(),
                    displayed,
                    labelSize);
        } else {
            if (context.logEnabled) {
                IkGuiImplLogging.logText("---");
            }
            if (separatorThickness > 0.0f) {
                window.drawList.addLineH(
                        separator1X1,
                        separator2X2,
                        separatorsY,
                        separatorColor,
                        separatorThickness);
            }
        }
    }

    /**
     * Behavior for a splitter between two areas, which can be dragged to resize them. Using a hover
     * visibility delay allows us to hide the highlight and mouse cursor for a short time, which can
     * be convenient to reduce visual noise.
     *
     * @param bb The bounding box of the splitter.
     * @param id The ID of the splitter.
     * @param axis The axis the splitter moves along.
     * @param size1 The size of the first area, modified when dragged (array of length 1).
     * @param size2 The size of the second area, modified when dragged (array of length 1).
     * @param minSize1 The minimum size of the first area.
     * @param minSize2 The minimum size of the second area.
     * @param hoverExtend How far to extend the hover area of the splitter along the axis.
     * @param hoverVisibilityDelay How long the splitter must be hovered before it is highlighted,
     *     in milliseconds.
     * @param backgroundColor A color to draw behind the splitter, or clear for none.
     * @return True while the splitter is held.
     */
    static boolean splitterBehavior(
            @NonNull RectFloat bb,
            int id,
            @NonNull Axis axis,
            float @NonNull [] size1,
            float @NonNull [] size2,
            float minSize1,
            float minSize2,
            float hoverExtend,
            long hoverVisibilityDelay,
            int backgroundColor) {
        final Window window = context.windowCurrent;

        if (!IkGuiInternal.itemAdd(bb, id, null, ItemFlags.NO_NAV)) {
            return false;
        }

        final int buttonFlags = ButtonFlags.INTERNAL_FLATTEN_CHILDREN;
        final IkBoolean hovered = new IkBoolean();
        final IkBoolean held = new IkBoolean();
        final RectFloat bbInteract = new RectFloat(bb);
        if (axis == Axis.Y) {
            bbInteract.expand(0.0f, hoverExtend);
        } else {
            bbInteract.expand(hoverExtend, 0.0f);
        }
        IkGuiInternal.buttonBehavior(bbInteract, id, hovered, held, buttonFlags);
        if (hovered.get()) {
            // For isItemHovered(), because the interaction box is larger than the bounding box
            context.lastItemData.statusFlags |= ItemStatusFlags.HOVERED_RECT;
        }

        if (held.get()
                || (hovered.get()
                        && context.hoveredIDPreviousFrame == id
                        && context.hoveredIDTimer >= hoverVisibilityDelay)) {
            IkGuiImplUtils.setMouseCursor(
                    axis == Axis.Y ? MouseCursor.RESIZE_NS : MouseCursor.RESIZE_EW);
        }

        final RectFloat bbRender = new RectFloat(bb);
        if (held.get()) {
            float mouseDelta =
                    axis == Axis.X
                            ? context.io.mousePosition.x
                                    - context.activeIDClickOffset.x
                                    - bbInteract.getLeft()
                            : context.io.mousePosition.y
                                    - context.activeIDClickOffset.y
                                    - bbInteract.getTop();

            // Minimum pane size
            final float size1MaximumDelta = Math.max(0.0f, size1[0] - minSize1);
            final float size2MaximumDelta = Math.max(0.0f, size2[0] - minSize2);
            if (mouseDelta < -size1MaximumDelta) {
                mouseDelta = -size1MaximumDelta;
            }
            if (mouseDelta > size2MaximumDelta) {
                mouseDelta = size2MaximumDelta;
            }

            // Apply the resize
            if (mouseDelta != 0.0f) {
                size1[0] = Math.max(size1[0] + mouseDelta, minSize1);
                size2[0] = Math.max(size2[0] - mouseDelta, minSize2);
                if (axis == Axis.X) {
                    bbRender.translate(mouseDelta, 0.0f);
                } else {
                    bbRender.translate(0.0f, mouseDelta);
                }
                IkGuiInternal.markItemEdited(id);
            }
        }

        // Render at the new position
        if ((backgroundColor & 0xFF) != 0) {
            window.drawList.addRectFilled(
                    bbRender.getLeft(),
                    bbRender.getTop(),
                    bbRender.getRight(),
                    bbRender.getBottom(),
                    backgroundColor,
                    0.0f);
        }
        final ColorType colorType =
                held.get()
                        ? ColorType.SEPARATOR_ACTIVE
                        : (hovered.get() && context.hoveredIDTimer >= hoverVisibilityDelay)
                                ? ColorType.SEPARATOR_HOVERED
                                : ColorType.SEPARATOR;
        window.drawList.addRectFilled(
                bbRender.getLeft(),
                bbRender.getTop(),
                bbRender.getRight(),
                bbRender.getBottom(),
                IkGuiImplUtils.getColorWithGlobalAlpha(colorType),
                0.0f);

        return held.get();
    }

    /** Add vertical spacing. */
    public static void spacing() {
        final Window window = IkGuiInternal.getCurrentWindow();
        if (window.skipItems) {
            return;
        }
        IkGuiInternal.itemSize(0, 0);
    }

    /**
     * Move the content position back to the left.
     *
     * @param width The amount to unindent, or 0 to use the style indent spacing.
     */
    public static void unindent(float width) {
        final Window window = IkGuiInternal.getCurrentWindow();
        window.indent -= width != 0.0f ? width : context.style.variable.indentSpacing;
        window.cursorPosition.x = window.position.x + window.indent + window.columnsOffset;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplLayout() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
