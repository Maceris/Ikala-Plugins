package com.ikalagaming.graphics.gui.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Returned by beginMultiSelect() and endMultiSelect(), this mainly contains a list of selection
 * requests to apply to your selection. Don't hold on to this over multiple frames, or past another
 * call to beginMultiSelect() or endMultiSelect().
 *
 * <p>Use the Selection category of the debug log to see requests as they happen. Some fields are
 * only useful if your list is dynamic and allows deletion.
 */
public class MultiSelectIO {
    /** Requests to apply to your selection data. */
    public final List<SelectionRequest> requests;

    /**
     * Set by beginMultiSelect(): the source item, which is often the first selected item. If using
     * a clipper, this must never be clipped, so pass it to includeItemByIndex().
     *
     * @see SelectionUserData#INVALID
     */
    public long rangeSourceItem;

    /**
     * Set by beginMultiSelect(): the last known selection user data of the focused item, if it was
     * submitted. Useful to restore focus after deleting items.
     */
    public long navIDItem;

    /** Set by beginMultiSelect(): the last known selection state of the focused item. */
    public boolean navIDSelected;

    /**
     * Set by the application before endMultiSelect() to reset the range source item, for example
     * after deleting the selection.
     */
    public boolean rangeSourceReset;

    /**
     * The item count passed to beginMultiSelect(), stored here for convenience when applying
     * requests. Not used internally.
     */
    public int itemsCount;

    /** Create an empty IO structure. */
    public MultiSelectIO() {
        requests = new ArrayList<>();
        rangeSourceItem = SelectionUserData.INVALID;
        navIDItem = SelectionUserData.INVALID;
        navIDSelected = false;
        rangeSourceReset = false;
        itemsCount = 0;
    }

    /** Reset everything except the item count. */
    public void clear() {
        requests.clear();
        rangeSourceItem = SelectionUserData.INVALID;
        navIDItem = SelectionUserData.INVALID;
        navIDSelected = false;
        rangeSourceReset = false;
    }
}
