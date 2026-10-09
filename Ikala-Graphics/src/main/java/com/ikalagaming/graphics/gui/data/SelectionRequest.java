package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.SelectionRequestType;

/**
 * A request to change the selection, returned in {@link MultiSelectIO#requests} by
 * beginMultiSelect() and endMultiSelect(). You will most often receive a clear followed by a single
 * item range.
 *
 * @param type The type of request.
 * @param selected Whether to select (true) or unselect (false) the items.
 * @param rangeDirection For SET_RANGE requests, +1 when the first item comes before the last item,
 *     -1 otherwise. Useful to preserve the selection order on a backwards Shift+Click.
 * @param rangeFirstItem For SET_RANGE requests, the first item in the range (inclusive). This is
 *     generally the range source item when shift selecting from top to bottom.
 * @param rangeLastItem For SET_RANGE requests, the last item in the range (inclusive). This is
 *     generally the range source item when shift selecting from bottom to top.
 */
public record SelectionRequest(
        SelectionRequestType type,
        boolean selected,
        int rangeDirection,
        long rangeFirstItem,
        long rangeLastItem) {}
