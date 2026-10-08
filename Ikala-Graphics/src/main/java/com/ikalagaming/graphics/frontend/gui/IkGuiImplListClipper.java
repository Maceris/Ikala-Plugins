package com.ikalagaming.graphics.frontend.gui;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ListClipperFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags;
import com.ikalagaming.graphics.frontend.gui.util.MathUtil;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** The implementation of {@link ListClipper}. */
@Slf4j
class IkGuiImplListClipper {

    static Context context;

    /**
     * Whether items should be skipped. This prevents us from using a list clipper inside a table
     * cell, since without a begin/end scheme for rows, using the clipper is ambiguous.
     *
     * @return True if we should skip items.
     */
    private static boolean getSkipItemForListClipping() {
        return context.currentTable != null
                ? context.currentTable.hostSkipItems
                : context.windowCurrent.skipItems;
    }

    /**
     * Sort ranges and fuse them together where possible.
     *
     * @param ranges The ranges.
     * @param offset The number of ranges at the start of the list to ignore.
     */
    private static void sortAndFuseRanges(@NonNull List<ListClipperRange> ranges, int offset) {
        if (ranges.size() - offset <= 1) {
            return;
        }

        // Bubble sort is fine as we are only sorting 2-3 entries
        for (int sortEnd = ranges.size() - offset - 1; sortEnd > 0; --sortEnd) {
            for (int i = offset; i < sortEnd + offset; ++i) {
                if (ranges.get(i).min > ranges.get(i + 1).min) {
                    final ListClipperRange temp = ranges.get(i);
                    ranges.set(i, ranges.get(i + 1));
                    ranges.set(i + 1, temp);
                }
            }
        }

        // Fuse ranges together as much as possible
        for (int i = 1 + offset; i < ranges.size(); ++i) {
            final ListClipperRange previous = ranges.get(i - 1);
            final ListClipperRange current = ranges.get(i);
            if (previous.max < current.min) {
                continue;
            }
            previous.min = Math.min(previous.min, current.min);
            previous.max = Math.max(previous.max, current.max);
            ranges.remove(i);
            --i;
        }
    }

    /**
     * Set the cursor position, and a few other things so that setScrollHereY() works when seeking
     * the cursor.
     *
     * @param clipper The clipper.
     * @param posY The y position to seek to.
     * @param lineHeight The height of a line.
     */
    private static void seekCursorAndSetupPrevLine(
            @NonNull ListClipper clipper, float posY, float lineHeight) {
        final Window window = context.windowCurrent;
        final float offsetY = posY - window.cursorPosition.y;
        window.cursorPosition.y = posY;
        window.cursorMaxPosition.y =
                Math.max(window.cursorMaxPosition.y, posY - context.style.variable.itemSpacing.y);
        // Setting these so that setScrollHereY() can function after the end of the clipper
        window.cursorPreviousLinePosition.y = window.cursorPosition.y - lineHeight;
        window.lineSizePrevious.y = lineHeight - context.style.variable.itemSpacing.y;
        final Table table = context.currentTable;
        if (table != null) {
            if (table.isInsideRow) {
                IkGuiImplTables.tableEndRow(table);
            }
            final int rowIncrease = (int) ((offsetY / lineHeight) + 0.5f);
            // If the clipper item height is different from the table row height, consider using
            // ListClipperFlags.NO_SET_TABLE_ROW_COUNTERS
            if (rowIncrease > 0
                    && (clipper.flags & ListClipperFlags.NO_SET_TABLE_ROW_COUNTERS) == 0) {
                table.currentRow += rowIncrease;
                table.rowBackgroundColorCounter += rowIncrease;
            }
            table.rowPosY2 = window.cursorPosition.y;
        }
    }

    static void begin(@NonNull ListClipper clipper, int itemsCount, float itemsHeight) {
        if (context.windowCurrent != null) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_CLIPPER,
                    "Clipper: Begin(%d,%.2f) in '%s'",
                    itemsCount,
                    itemsHeight,
                    context.windowCurrent.name);
        }
        final Window window = context.windowCurrent;

        final Table table = context.currentTable;
        if (table != null && table.isInsideRow) {
            IkGuiImplTables.tableEndRow(table);
        }

        clipper.startPosY = window.cursorPosition.y;
        clipper.itemsHeight = itemsHeight;
        clipper.itemsCount = itemsCount;
        clipper.displayStart = -1;
        clipper.displayEnd = 0;

        // Acquire temporary buffer
        if (++context.clipperTempDataStacked > context.clipperTempData.size()) {
            context.clipperTempData.add(new ListClipperData());
        }
        final ListClipperData data =
                context.clipperTempData.get(context.clipperTempDataStacked - 1);
        data.reset(clipper);
        data.lossynessOffset = context.windowCurrent.cursorStartPositionLossyness.y;
        clipper.tempData = data;
        clipper.startSeekOffsetY = data.lossynessOffset;
    }

    static void end(@NonNull ListClipper clipper) {
        if (context.windowCurrent != null && clipper.tempData != null) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_CLIPPER,
                    "Clipper: End() in '%s'",
                    context.windowCurrent.name);
        }
        final ListClipperData data = clipper.tempData;
        if (data != null) {
            // We seek at the end instead of asserting that we are already at the right position
            if (clipper.itemsCount >= 0
                    && clipper.itemsCount < Integer.MAX_VALUE
                    && clipper.displayStart >= 0) {
                seekCursorForItem(clipper, clipper.itemsCount);
            }

            // Restore the temporary buffer and fix back references which may be invalidated when
            // nesting
            if (data.listClipper != clipper) {
                IkGuiImplDebugTools.reportError(
                        log, "ListClipper end() called on the wrong clipper");
            }
            data.stepNumber = data.ranges.size();
            if (--context.clipperTempDataStacked > 0) {
                final ListClipperData parent =
                        context.clipperTempData.get(context.clipperTempDataStacked - 1);
                parent.listClipper.tempData = parent;
            }
            clipper.tempData = null;
        }
        // Clear this so code which may be reused past the last step() won't trip on a range
        clipper.displayStart = clipper.itemsCount;
        clipper.displayEnd = clipper.itemsCount;
        clipper.itemsCount = -1;
    }

    static void includeItemsByIndex(@NonNull ListClipper clipper, int itemBegin, int itemEnd) {
        final ListClipperData data = clipper.tempData;
        if (data == null || clipper.displayStart >= 0) {
            IkGuiImplDebugTools.reportError(
                    log, "includeItemsByIndex() is only allowed after begin() and before step()");
            return;
        }
        if (itemBegin > itemEnd) {
            IkGuiImplDebugTools.reportError(
                    log, "includeItemsByIndex() with an end before the beginning");
            return;
        }
        if (itemBegin < itemEnd) {
            data.ranges.add(ListClipperRange.fromIndices(itemBegin, itemEnd));
        }
    }

    static void seekCursorForItem(@NonNull ListClipper clipper, int itemIndex) {
        // startPosY starts from itemsFrozen, by adding startSeekOffsetY we generally cancel that
        // out. We store the offset so that seeking is possible after the last step.
        final float posY =
                (float)
                        (clipper.startPosY
                                + clipper.startSeekOffsetY
                                + (double) itemIndex * clipper.itemsHeight);
        seekCursorAndSetupPrevLine(clipper, posY, clipper.itemsHeight);
    }

    /**
     * Check if a float is too big to store every integer exactly.
     *
     * @param value The value.
     * @return True if some integers near the value can't be represented.
     */
    private static boolean isFloatAboveGuaranteedIntegerPrecision(float value) {
        return value <= -16_777_216 || value >= 16_777_216;
    }

    private static boolean stepInternal(@NonNull ListClipper clipper) {
        final Window window = context.windowCurrent;
        final ListClipperData data = clipper.tempData;
        if (data == null) {
            IkGuiImplDebugTools.reportError(
                    log, "ListClipper step() called too many times, or before begin()");
            return false;
        }

        final Table table = context.currentTable;
        if (table != null && table.isInsideRow) {
            IkGuiImplTables.tableEndRow(table);
        }

        // No items
        if (clipper.itemsCount == 0 || getSkipItemForListClipping()) {
            return false;
        }

        // While we are in frozen row state, keep displaying items one by one, unclipped
        if (data.stepNumber == 0 && table != null && !table.isUnfrozenRows) {
            clipper.displayStart = data.itemsFrozen;
            clipper.displayEnd = Math.min(data.itemsFrozen + 1, clipper.itemsCount);
            if (clipper.displayStart < clipper.displayEnd) {
                data.itemsFrozen++;
            }
            return true;
        }

        // Step 0: let the user process the first element (regardless of it being visible or not,
        // so we can measure the element height)
        boolean calcClipping = false;
        if (data.stepNumber == 0) {
            clipper.startPosY = window.cursorPosition.y;
            if (clipper.itemsHeight <= 0.0f) {
                // Submit the first item (or range) so we can measure its height
                data.ranges.addFirst(
                        ListClipperRange.fromIndices(data.itemsFrozen, data.itemsFrozen + 1));
                clipper.displayStart = Math.max(data.ranges.getFirst().min, data.itemsFrozen);
                clipper.displayEnd = Math.min(data.ranges.getFirst().max, clipper.itemsCount);
                data.stepNumber = 1;
                return true;
            }
            // On the first step with a known item height, calculate clipping
            calcClipping = true;
        }

        // Step 1: let the clipper infer the height from the first range
        if (clipper.itemsHeight <= 0.0f) {
            final boolean affectedByFloatingPointPrecision =
                    isFloatAboveGuaranteedIntegerPrecision((float) clipper.startPosY)
                            || isFloatAboveGuaranteedIntegerPrecision(window.cursorPosition.y);
            if (affectedByFloatingPointPrecision) {
                // A mitigation for very large ranges, assume the last line height is the item
                // height. This wouldn't allow items with multiple lines.
                clipper.itemsHeight =
                        window.lineSizePrevious.y + context.style.variable.itemSpacing.y;
                window.cursorPosition.y = (float) (clipper.startPosY + clipper.itemsHeight);
            } else {
                clipper.itemsHeight =
                        (float) (window.cursorPosition.y - clipper.startPosY)
                                / (float) (clipper.displayEnd - clipper.displayStart);
            }
            // Accept that no items have been submitted if in indeterminate mode
            if (clipper.itemsHeight == 0.0f && clipper.itemsCount == Integer.MAX_VALUE) {
                return false;
            }
            if (clipper.itemsHeight <= 0.0f) {
                IkGuiImplDebugTools.reportError(
                        log,
                        "ListClipper failed to calculate the item height! The first item hasn't"
                                + " been submitted, or has not moved the cursor vertically");
                return false;
            }
            // If the item height had to be calculated, calculate clipping afterwards
            calcClipping = true;
        }

        // Step 0 or 1: calculate the actual ranges of visible elements
        final int alreadySubmitted = clipper.displayEnd;
        if (calcClipping) {
            // Record the seek offset, so seekCursorForItem() can be called after the data is done
            clipper.startSeekOffsetY =
                    (double) data.lossynessOffset - data.itemsFrozen * (double) clipper.itemsHeight;

            if (context.logEnabled) {
                // If logging is active, do not perform any clipping
                data.ranges.add(ListClipperRange.fromIndices(0, clipper.itemsCount));
            } else {
                // Add the range selected to be included for navigation
                final boolean isNavRequest =
                        context.navMoveScoringItems
                                && context.navFocusedWindow != null
                                && context.navFocusedWindow.rootWindowForNavigation
                                        == window.rootWindowForNavigation;
                final int navOffsetMin =
                        isNavRequest && context.navMoveClipDirection == Direction.UP ? -1 : 0;
                final int navOffsetMax =
                        isNavRequest && context.navMoveClipDirection == Direction.DOWN ? 1 : 0;
                if (isNavRequest) {
                    data.ranges.add(
                            ListClipperRange.fromPositions(
                                    context.navScoringRect.getTop(),
                                    context.navScoringRect.getBottom(),
                                    navOffsetMin,
                                    navOffsetMax));
                    final RectFloat noClip = context.navScoringNoClipRect;
                    if (noClip.getLeft() <= noClip.getRight()
                            && noClip.getTop() <= noClip.getBottom()) {
                        data.ranges.add(
                                ListClipperRange.fromPositions(
                                        noClip.getTop(),
                                        noClip.getBottom(),
                                        navOffsetMin,
                                        navOffsetMax));
                    }
                }
                if (isNavRequest
                        && (context.navMoveFlags & NavMoveFlags.IS_TABBING) != 0
                        && context.navTabbingDirection == -1) {
                    data.ranges.add(
                            ListClipperRange.fromIndices(
                                    clipper.itemsCount - 1, clipper.itemsCount));
                }

                // Add the focused/active item
                if (context.navID != 0 && window.navLastIDs[0] == context.navID) {
                    final RectFloat navRectAbsolute =
                            IkGuiImplNav.windowRectRelToAbs(
                                    window, window.navRectRelative[0], new RectFloat(0, 0, 0, 0));
                    data.ranges.add(
                            ListClipperRange.fromPositions(
                                    navRectAbsolute.getTop(), navRectAbsolute.getBottom(), 0, 0));
                }

                float minY = window.rectCurrentClip.getTop();
                float maxY = window.rectCurrentClip.getBottom();

                // Add the box selection range
                final BoxSelectState bs = context.boxSelectState;
                if (bs.isActive && bs.window == window) {
                    // Selectable() use of half item spacing isn't consistent in layout, as the
                    // itemAdd() rectangle strays above the itemSize() cursor position. Box-select
                    // relies on comparing overlap of the previous and current rectangles and is
                    // sensitive to that, so we add half the item spacing on each side.
                    final float padY = context.style.variable.itemSpacing.y;
                    minY -= padY;
                    maxY += padY;

                    // Box-select on a 2D area requires different clipping
                    if (bs.unclipMode) {
                        data.ranges.add(
                                ListClipperRange.fromPositions(
                                        bs.unclipRect.getTop() - padY,
                                        bs.unclipRect.getBottom() + padY,
                                        0,
                                        0));
                    }
                }

                // Add the main visible range
                data.ranges.add(
                        ListClipperRange.fromPositions(minY, maxY, navOffsetMin, navOffsetMax));
            }

            // Convert position ranges to item index ranges
            // - When a starting position is after our maximum item, we set min to the last item.
            //   This allows us to handle most forms of wrapping.
            // - Due to selectable extra padding they tend to be unaligned with exact units in the
            //   item list, which with flooring/ceiling tends to lead to 2 items being submitted
            //   instead of one.
            for (ListClipperRange range : data.ranges) {
                if (!range.positionToIndexConvert) {
                    continue;
                }
                final int m1 =
                        (int)
                                (((double) range.min
                                                - window.cursorPosition.y
                                                - data.lossynessOffset)
                                        / clipper.itemsHeight);
                final int m2 =
                        (int)
                                ((((double) range.max
                                                        - window.cursorPosition.y
                                                        - data.lossynessOffset)
                                                / clipper.itemsHeight)
                                        + 0.999_999f);
                range.min =
                        MathUtil.clamp(
                                alreadySubmitted + m1 + range.positionToIndexOffsetMin,
                                alreadySubmitted,
                                Math.max(alreadySubmitted, clipper.itemsCount - 1));
                range.max =
                        MathUtil.clamp(
                                alreadySubmitted + m2 + range.positionToIndexOffsetMax,
                                range.min + 1,
                                Math.max(range.min + 1, clipper.itemsCount));
                range.positionToIndexConvert = false;
            }
            sortAndFuseRanges(data.ranges, data.stepNumber);
        }

        // Step 0+ (if the item height is given in advance) or 1+: display the next range in line
        while (data.stepNumber < data.ranges.size()) {
            final ListClipperRange range = data.ranges.get(data.stepNumber);
            clipper.displayStart = Math.max(range.min, alreadySubmitted);
            clipper.displayEnd = Math.min(range.max, clipper.itemsCount);
            data.stepNumber++;
            if (clipper.displayStart >= clipper.displayEnd) {
                continue;
            }
            if (clipper.displayStart > alreadySubmitted) {
                seekCursorForItem(clipper, clipper.displayStart);
            }
            return true;
        }

        // After the last step: advance the cursor to the end of the list and then return false to
        // end the loop
        if (clipper.itemsCount < Integer.MAX_VALUE) {
            seekCursorForItem(clipper, clipper.itemsCount);
        }

        return false;
    }

    static boolean step(@NonNull ListClipper clipper) {
        boolean result = stepInternal(clipper);
        if (result && clipper.displayStart >= clipper.displayEnd) {
            result = false;
        }
        if (result) {
            IkGuiImplDebugTools.debugLog(
                    DebugLogFlags.EVENT_CLIPPER,
                    "Clipper: Step(): display %d to %d.",
                    clipper.displayStart,
                    clipper.displayEnd);
        } else {
            IkGuiImplDebugTools.debugLog(DebugLogFlags.EVENT_CLIPPER, "Clipper: Step(): End.");
        }
        if (!result) {
            end(clipper);
        }
        return result;
    }

    /** Private constructor so this is not instantiated. */
    private IkGuiImplListClipper() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
