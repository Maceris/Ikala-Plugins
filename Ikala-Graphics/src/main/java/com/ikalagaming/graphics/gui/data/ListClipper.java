package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.IkGuiInternal;

/**
 * Helper to manually clip large lists of items. If you have lots of evenly spaced items and you
 * have random access to the list, you can perform coarse clipping based on visibility to only
 * submit items that are in view. The clipper calculates the range of visible items and advances the
 * cursor to compensate for the non-visible items we have skipped.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ListClipper clipper = new ListClipper();
 * clipper.begin(1000); // We have 1000 elements, evenly spaced
 * while (clipper.step()) {
 *     for (int i = clipper.displayStart; i < clipper.displayEnd; ++i) {
 *         IkGui.text("line number " + i);
 *     }
 * }
 * }</pre>
 *
 * Generally what happens is:
 *
 * <ul>
 *   <li>The clipper lets you process the first element (displayStart = 0, displayEnd = 1)
 *       regardless of it being visible or not, so it can measure the height of the element.
 *   <li>The clipper calculates the actual range of elements to display based on the current clip
 *       rect, and positions the cursor before the first visible element.
 *   <li>User code submits the visible elements.
 *   <li>The clipper also handles various subtleties related to keyboard/gamepad navigation,
 *       wrapping, etc.
 * </ul>
 */
public class ListClipper {
    /** First item to display, updated by each call to step(). */
    public int displayStart;

    /** End of items to display (exclusive). */
    public int displayEnd;

    /** Helper storage for user convenience/code. Optional, and otherwise unused. */
    public int userIndex;

    /** Internal: number of items. */
    public int itemsCount;

    /** Internal: height of items, calculated after the first step if not specified. */
    public float itemsHeight;

    /**
     * Internal: flags.
     *
     * @see com.ikalagaming.graphics.gui.flags.ListClipperFlags
     */
    public int flags;

    /**
     * Internal: cursor position at the time of begin(), or after table frozen rows are processed.
     */
    public double startPosY;

    /** Internal: accounts for frozen rows in a table and initial loss of precision. */
    public double startSeekOffsetY;

    /** Internal: temporary data while the clipper is active. */
    public ListClipperData tempData;

    public ListClipper() {
        displayStart = 0;
        displayEnd = 0;
        itemsCount = -1;
    }

    /**
     * Start clipping, with the item height calculated automatically on the first step.
     *
     * @param itemsCount The number of items. Use Integer.MAX_VALUE if you don't know how many items
     *     you have, in which case the cursor won't be advanced in the final step, and you can call
     *     seekCursorForItem() manually if you need.
     */
    public void begin(int itemsCount) {
        begin(itemsCount, -1.0f);
    }

    /**
     * Start clipping.
     *
     * @param itemsCount The number of items. Use Integer.MAX_VALUE if you don't know how many items
     *     you have, in which case the cursor won't be advanced in the final step, and you can call
     *     seekCursorForItem() manually if you need.
     * @param itemsHeight The distance between items, typically getTextLineHeightWithSpacing() or
     *     getFrameHeightWithSpacing(). Use -1 to calculate it automatically on the first step.
     */
    public void begin(int itemsCount, float itemsHeight) {
        IkGuiInternal.listClipperBegin(this, itemsCount, itemsHeight);
    }

    /** Stop clipping. Automatically called on the last call of step() that returns false. */
    public void end() {
        IkGuiInternal.listClipperEnd(this);
    }

    /**
     * Mark an item as not clipped, regardless of its visibility. Call before the first call to
     * step().
     *
     * @param itemIndex The item index.
     */
    public void includeItemByIndex(int itemIndex) {
        includeItemsByIndex(itemIndex, itemIndex + 1);
    }

    /**
     * Mark a range of items as not clipped, regardless of their visibility. Call before the first
     * call to step(). Due to alignment/padding of certain items it is possible that an extra item
     * may be included on either end of the display range.
     *
     * @param itemBegin The first item, inclusive.
     * @param itemEnd The last item, exclusive.
     */
    public void includeItemsByIndex(int itemBegin, int itemEnd) {
        IkGuiInternal.listClipperIncludeItemsByIndex(this, itemBegin, itemEnd);
    }

    /**
     * Seek the cursor toward the given item. This is automatically called while stepping. The only
     * reason to call this is if you used begin(Integer.MAX_VALUE) because you don't know the item
     * count ahead of time, in which case you'll want to call seekCursorForItem(itemCount) after all
     * steps are done.
     *
     * @param itemIndex The item index.
     */
    public void seekCursorForItem(int itemIndex) {
        IkGuiInternal.listClipperSeekCursorForItem(this, itemIndex);
    }

    /**
     * Call until it returns false. The displayStart/displayEnd fields will be set and you can
     * process/draw those items.
     *
     * @return True if there are items to display.
     */
    public boolean step() {
        return IkGuiInternal.listClipperStep(this);
    }
}
