package com.ikalagaming.graphics.frontend.gui.callback;

/**
 * Applies a selection change to an item in your own storage, for {@link
 * com.ikalagaming.graphics.frontend.gui.data.SelectionExternalStorage}.
 */
@FunctionalInterface
public interface SelectionAdapterSetItemSelected {
    /**
     * Select or unselect an item.
     *
     * @param index The index of the item.
     * @param selected Whether the item is now selected.
     */
    void setItemSelected(int index, boolean selected);
}
