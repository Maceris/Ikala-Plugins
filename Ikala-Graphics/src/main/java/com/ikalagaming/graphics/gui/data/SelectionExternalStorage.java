package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.callback.SelectionAdapterSetItemSelected;
import com.ikalagaming.graphics.gui.enums.SelectionRequestType;

import lombok.NonNull;

/**
 * An optional helper to apply multi-selection requests to existing randomly accessible storage.
 * This is convenient to quickly wire up multi-select to e.g. an array of booleans, or items that
 * store their own selection state. Selection user data is treated as item indices.
 */
public class SelectionExternalStorage {
    /**
     * Applies each change to your storage. For example: {@code selection.adapterSetItemSelected =
     * (index, selected) -> items.get(index).selected = selected;}
     */
    @NonNull public SelectionAdapterSetItemSelected adapterSetItemSelected;

    /**
     * Create a helper that applies changes with the given adapter.
     *
     * @param adapterSetItemSelected Applies each change to your storage.
     */
    public SelectionExternalStorage(
            @NonNull SelectionAdapterSetItemSelected adapterSetItemSelected) {
        this.adapterSetItemSelected = adapterSetItemSelected;
    }

    /**
     * Apply the selection requests from beginMultiSelect() and endMultiSelect(), using the adapter.
     * This uses the item count passed to beginMultiSelect() to select or clear all items.
     *
     * @param io The multi-select IO with requests to apply.
     */
    public void applyRequests(@NonNull MultiSelectIO io) {
        for (SelectionRequest request : io.requests) {
            if (request.type() == SelectionRequestType.SET_ALL) {
                for (int index = 0; index < io.itemsCount; ++index) {
                    adapterSetItemSelected.setItemSelected(index, request.selected());
                }
            } else if (request.type() == SelectionRequestType.SET_RANGE) {
                for (int index = (int) request.rangeFirstItem();
                        index <= (int) request.rangeLastItem();
                        ++index) {
                    adapterSetItemSelected.setItemSelected(index, request.selected());
                }
            }
        }
    }
}
