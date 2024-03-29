package com.ikalagaming.rpg.inventory;

import com.ikalagaming.rpg.item.Equipment;
import com.ikalagaming.rpg.item.Item;

import lombok.Getter;
import lombok.NonNull;

/**
 * A single slot within an inventory that may or may not contain an item.
 *
 * @author Ches Burks
 */
@Getter
class InventorySlot {

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
        if (source.isEmpty()) {
            // Why did you call the method then?
            return true;
        }
        if (destination.isEmpty()) {
            InventorySlot.swapContents(source, destination);
            return true;
        }
        // Both slots contain items if we got to this point
        if (!source.isStackable() || !destination.isStackable()) {
            // At least one of the slots isn't stackable, so we can't combine
            return false;
        }

        Item sourceItem = source.getItem();
        Item destItem = destination.getItem();

        if (!sourceItem.getID().equals(destItem.getID())) {
            return false;
        }

        final int maxStackSize = InvUtil.maxStackSize(destItem);

        ItemStack sourceStack = source.getItemStack();
        ItemStack destStack = destination.getItemStack();

        if (destStack.getCount() >= maxStackSize) {
            return false;
        }

        /*
         * We can't move more than the source has in it, and we can't add more
         * than the destination can fit. So we take the smaller of the two
         * numbers and work out if we have emptied the source later.
         */
        final int itemsToTransfer =
                Math.min(sourceStack.getCount(), maxStackSize - destStack.getCount());

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
        final Equipment tempIndividual = slot1.individual;
        final boolean tempStackable = slot1.stackable;
        final boolean tempEmpty = slot1.empty;

        slot1.stack = slot2.stack;
        slot1.individual = slot2.individual;
        slot1.stackable = slot2.stackable;
        slot1.empty = slot2.empty;

        slot2.stack = tempStack;
        slot2.individual = tempIndividual;
        slot2.stackable = tempStackable;
        slot2.empty = tempEmpty;
    }

    /**
     * The item stack, which is only present if this is not empty and contains a stackable item.
     *
     * @return The item stack, or null if empty or non-stackable.
     */
    private ItemStack stack;

    /**
     * The individual item, which is only present if this is not empty and contains a non-stackable
     * item.
     *
     * @return The item, or null if
     */
    private Equipment individual;

    /**
     * Whether the inventory slot contains a stackable item. This will be false if the slot is
     * empty, but is also meaningless in that case.
     *
     * @return Whether the slot contains an item stack.
     */
    private boolean stackable;

    /**
     * Whether the slot is empty. True if there is nothing in the slot, false if it contains either
     * a stack or individual item.
     *
     * @return Whether the slot is empty.
     */
    private boolean empty;

    /** Create a new empty inventory slot. */
    public InventorySlot() {
        empty = true;
        stackable = false;
        individual = null;
        stack = null;
    }

    /** Clear anything in the inventory slot, rendering it empty. */
    public void clear() {
        individual = null;
        stack = null;
        stackable = false;
        empty = true;
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
        if (!isStackable()) {
            return 1;
        }
        return stack.getCount();
    }

    /**
     * Fetch the item that this slot contains.
     *
     * @return The item, which is either what is contained by the stack for stackable items, or the
     *     non-stackable item. Null for empty slots.
     * @see #getItemStack()
     */
    public Item getItem() {
        if (isEmpty()) {
            return null;
        }
        if (isStackable()) {
            return stack.getItem();
        }
        return individual;
    }

    /**
     * Fetch the item stack that this slot contains, if it's stackable. If the slot is empty or not
     * stackable, this will return null.
     *
     * @return The item stack, or null.
     */
    public ItemStack getItemStack() {
        if (isEmpty() || !isStackable()) {
            return null;
        }
        return stack;
    }

    /**
     * Set the item in this slot, which only works when the slot is empty.
     *
     * @param item The non-stackable item to store.
     * @return Whether storing the item was successful.
     */
    public boolean setItem(@NonNull Equipment item) {
        if (!isEmpty()) {
            return false;
        }
        individual = item;
        stack = null;
        stackable = false;
        empty = false;
        return true;
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
        individual = null;
        this.stack = stack;
        stackable = true;
        empty = false;
        return true;
    }
}
