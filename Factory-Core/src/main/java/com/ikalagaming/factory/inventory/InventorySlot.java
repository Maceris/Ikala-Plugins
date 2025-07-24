package com.ikalagaming.factory.inventory;

import com.ikalagaming.factory.item.Item;
import com.ikalagaming.factory.item.ItemStack;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class InventorySlot {

    /**
     * Combine items from the source slot into the destination slot. If the source is empty, nothing
     * happens. If the slots don't contain items that can stack together, nothing happens. If we can
     * move some or all of the items over, we do so until the destination is full. Any leftover
     * items that exceed the max stack size will be left in the source slot.
     *
     * <p>The return value indicates if the source slot is now empty, either because it was fully
     * moved/combined, or because it started that way.
     *
     * @param source The slot we are moving items out of.
     * @param destination The slot we are moving items in to.
     * @return Whether the source is now empty.
     */
    public static boolean combine(
            @NonNull InventorySlot source, @NonNull InventorySlot destination) {
        if (source == destination) {
            // same slot object, don't do anything
            return false;
        }
        ItemStack sourceStack = source.getItemStack();
        ItemStack destStack = destination.getItemStack();

        if (null == sourceStack) {
            // Why did you call the method then?
            return true;
        }
        if (null == destStack) {
            InventorySlot.swapContents(source, destination);
            return true;
        }
        // Both slots contain items if we got to this point

        if (!ItemStack.isSameType(sourceStack, destStack)) {
            return false;
        }

        if (destStack.getCount() >= ItemStack.MAX_STACK_SIZE) {
            return false;
        }

        /*
         * We can't move more than the source has in it, and we can't add more
         * than the destination can fit. So we take the smaller of the two
         * numbers and work out if we have emptied the source later.
         */
        final int itemsToTransfer =
                Math.min(sourceStack.getCount(), ItemStack.MAX_STACK_SIZE - destStack.getCount());

        destStack.setCount(destStack.getCount() + itemsToTransfer);
        sourceStack.setCount(sourceStack.getCount() - itemsToTransfer);

        if (sourceStack.getCount() <= 0) {
            source.clear();
            return true;
        }

        return false;
    }

    /**
     * Swap the contents of two inventory slots.
     *
     * @param slot1 The first slot.
     * @param slot2 The second slot.
     */
    public static void swapContents(@NonNull InventorySlot slot1, @NonNull InventorySlot slot2) {
        final ItemStack tempStack = slot1.stack;
        slot1.stack = slot2.stack;
        slot2.stack = tempStack;
    }

    /**
     * The item stack contained in this slot.
     *
     * @return The item stack, or null if empty.
     */
    private ItemStack stack;

    /** Create a new empty inventory slot. */
    public InventorySlot() {
        stack = null;
    }

    /** Clear anything in the inventory slot, rendering it empty. */
    public void clear() {
        stack = null;
    }

    /**
     * Return the number of items in this slot.
     *
     * @return The number of items this slot contains.
     */
    public int getCount() {
        if (isEmpty()) {
            return 0;
        }
        return stack.getCount();
    }

    /**
     * Fetch the item that this slot contains, or null if there is nothing in this slot.
     *
     * @return The item, that is contained in the stack, or null if there is no item.
     * @see #getItemStack()
     */
    public Item getItem() {
        if (isEmpty()) {
            return null;
        }
        return stack.getItem();
    }

    /**
     * Fetch the item stack that this slot contains. If the slot is empty this will return null.
     *
     * @return The item stack, or null.
     */
    public ItemStack getItemStack() {
        return stack;
    }

    /**
     * Returns true if the slot is empty, false if there is an item stack.
     *
     * @return Whether this inventory slot is empty.
     */
    public boolean isEmpty() {
        return stack == null;
    }

    /**
     * Set the item in this slot, which only works when the slot is empty.
     *
     * @param stack The stackable item to store.
     * @return Whether storing the item was successful.
     */
    public boolean setItemStack(@NonNull ItemStack stack) {
        if (!isEmpty()) {
            return false;
        }
        this.stack = stack;
        return true;
    }
}
