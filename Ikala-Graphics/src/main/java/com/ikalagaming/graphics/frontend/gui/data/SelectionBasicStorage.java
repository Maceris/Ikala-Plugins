package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGuiInternal;
import com.ikalagaming.graphics.frontend.gui.enums.SelectionRequestType;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.function.IntUnaryOperator;

/**
 * An optional helper to store a multi-selection and apply selection requests to it. Using this is
 * not required, your application can store the selection however it wants.
 *
 * <p>This always uses indices for the multi-selection API (passed to
 * setNextItemSelectionUserData(), and retrieved in {@link MultiSelectIO}), and uses {@link
 * #adapterIndexToStorageID} to convert an index to a persistent item ID that is stored in the
 * selection.
 *
 * <p>Iterate over the selected IDs with a for-each loop, or check {@link #contains(int)} for each
 * item. Items are returned in ID order, unless {@link #preserveOrder} is set.
 */
@Slf4j
public class SelectionBasicStorage implements Iterable<Integer> {
    /** If iterating returns items in the order they were selected, rather than by ID. */
    public boolean preserveOrder;

    /**
     * Converts an item index into the ID stored in the selection. By default, the index is the ID.
     * For example: {@code selection.adapterIndexToStorageID = index -> items.get(index).id;}
     */
    @NonNull public IntUnaryOperator adapterIndexToStorageID;

    /** Maps the IDs of selected items to the order they were selected in, which is always > 0. */
    private final Map<Integer, Integer> selection;

    /** Increasing counter to record the selection order. */
    private int selectionOrder;

    /** Create an empty selection, which uses indices as IDs. */
    public SelectionBasicStorage() {
        preserveOrder = false;
        adapterIndexToStorageID = index -> index;
        selection = new HashMap<>();
        selectionOrder = 1;
    }

    /**
     * Apply the selection requests from beginMultiSelect() and endMultiSelect(). This uses the item
     * count passed to beginMultiSelect() to select all items.
     *
     * @param io The multi-select IO with requests to apply.
     */
    public void applyRequests(@NonNull MultiSelectIO io) {
        for (SelectionRequest request : io.requests) {
            if (request.type() == SelectionRequestType.SET_ALL) {
                clear();
                if (request.selected()) {
                    if (io.itemsCount < 0) {
                        IkGuiInternal.reportError(
                                log, "Missing value for itemsCount in beginMultiSelect() call!");
                        continue;
                    }
                    for (int index = 0; index < io.itemsCount; ++index) {
                        setItemSelected(getStorageIDFromIndex(index), true, selectionOrder++);
                    }
                }
            } else if (request.type() == SelectionRequestType.SET_RANGE) {
                // Use the range direction to set the order, so that Shift+Clicking from 1 to 5 is
                // different from Shift+Clicking from 5 to 1
                final int first = (int) request.rangeFirstItem();
                final int last = (int) request.rangeLastItem();
                final int changes = last - first + 1;
                final int direction = request.rangeDirection() < 0 ? -1 : 1;
                int order = selectionOrder + (direction < 0 ? changes - 1 : 0);
                for (int index = first; index <= last; ++index, order += direction) {
                    setItemSelected(getStorageIDFromIndex(index), request.selected(), order);
                }
                if (request.selected()) {
                    selectionOrder += changes;
                }
            }
        }
    }

    /**
     * Check if an item is selected.
     *
     * @param id The ID of the item.
     * @return True if the item is in the selection.
     */
    public boolean contains(int id) {
        return selection.containsKey(id);
    }

    /** Clear the selection. */
    public void clear() {
        selection.clear();
        selectionOrder = 1;
    }

    /**
     * Swap the contents of two selections. The adapters and settings are not swapped.
     *
     * @param other The other selection.
     */
    public void swap(@NonNull SelectionBasicStorage other) {
        final Map<Integer, Integer> temp = new HashMap<>(selection);
        selection.clear();
        selection.putAll(other.selection);
        other.selection.clear();
        other.selection.putAll(temp);
        final int tempOrder = selectionOrder;
        selectionOrder = other.selectionOrder;
        other.selectionOrder = tempOrder;
    }

    /**
     * Add an item to the selection, or remove it. This is generally done by applyRequests().
     *
     * @param id The ID of the item.
     * @param selected Whether the item should be selected.
     */
    public void setItemSelected(int id, boolean selected) {
        setItemSelected(id, selected, selectionOrder);
        if (selected && selection.get(id) == selectionOrder) {
            ++selectionOrder;
        }
    }

    /**
     * Add an item to the selection with a specific order if it's not already there, or remove it.
     *
     * @param id The ID of the item.
     * @param selected Whether the item should be selected.
     * @param order The selection order to use, if the item is newly selected.
     */
    private void setItemSelected(int id, boolean selected, int order) {
        if (selected) {
            selection.putIfAbsent(id, order);
        } else {
            selection.remove(id);
        }
    }

    /**
     * The number of selected items.
     *
     * @return How many items are selected.
     */
    public int getSize() {
        return selection.size();
    }

    /**
     * Convert an item index to the item ID, using the adapter.
     *
     * @param index The item index.
     * @return The ID that is stored in the selection.
     */
    public int getStorageIDFromIndex(int index) {
        return adapterIndexToStorageID.applyAsInt(index);
    }

    /**
     * Fetch the selected IDs, in ID order or selection order depending on {@link #preserveOrder}.
     *
     * @return A new array of the selected IDs.
     */
    public int[] getSelectedItems() {
        if (!preserveOrder) {
            final int[] ids = new int[selection.size()];
            int i = 0;
            for (int id : selection.keySet()) {
                ids[i++] = id;
            }
            // IDs are unsigned hashes, so sort them that way
            return Arrays.stream(ids)
                    .mapToLong(Integer::toUnsignedLong)
                    .sorted()
                    .mapToInt(id -> (int) id)
                    .toArray();
        }
        return selection.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .mapToInt(Map.Entry::getKey)
                .toArray();
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        final int[] ids = getSelectedItems();
        return new PrimitiveIterator.OfInt() {
            private int next = 0;

            @Override
            public boolean hasNext() {
                return next < ids.length;
            }

            @Override
            public int nextInt() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return ids[next++];
            }
        };
    }
}
