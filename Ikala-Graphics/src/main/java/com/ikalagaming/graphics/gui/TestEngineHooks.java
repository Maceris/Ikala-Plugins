package com.ikalagaming.graphics.gui;

import com.ikalagaming.graphics.gui.data.LastItemData;
import com.ikalagaming.graphics.gui.util.RectFloat;

/**
 * Hooks that let a test engine track the items submitted each frame, to find and interact with them
 * by ID. The hooks are only called while {@code context.testEngineHookItems} is set.
 */
public interface TestEngineHooks {
    /**
     * Register an item's bounding box. Called for every item with an ID, including items that are
     * clipped.
     *
     * @param id The ID of the item.
     * @param bb The bounding box of the item, used for navigation.
     * @param itemData The last item data for the item, or null when there is none (e.g. windows).
     */
    void itemAdd(int id, RectFloat bb, LastItemData itemData);

    /**
     * Register an item's label and status, after the widget has processed it.
     *
     * @param id The ID of the item.
     * @param label The label of the item.
     * @param statusFlags The status flags of the item, including the test engine only flags like
     *     checked and opened.
     * @see com.ikalagaming.graphics.gui.flags.ItemStatusFlags
     */
    void itemInfo(int id, String label, int statusFlags);
}
