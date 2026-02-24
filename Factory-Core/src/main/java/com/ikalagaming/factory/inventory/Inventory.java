package com.ikalagaming.factory.inventory;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.Item;
import com.ikalagaming.factory.item.ItemStack;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class Inventory {

    /**
     * Swap slots between inventories. If they are the same inventory and slot, or either is an
     * invalid slot number, nothing happens.
     *
     * @param firstInventory The first inventory.
     * @param firstSlot The slot within the first inventory.
     * @param secondInventory The second inventory.
     * @param secondSlot The slot within the second inventory.
     */
    public static void swapSlots(
            @NonNull Inventory firstInventory,
            int firstSlot,
            @NonNull Inventory secondInventory,
            int secondSlot) {
        if (firstSlot < 0
                || firstSlot >= firstInventory.size
                || secondSlot < 0
                || secondSlot >= secondInventory.size
                || (firstInventory == secondInventory && firstSlot == secondSlot)) {
            return;
        }
        InventorySlot.swapContents(
                firstInventory.slots[firstSlot], secondInventory.slots[secondSlot]);
    }

    /**
     * The size of the inventory.
     *
     * @return The number of inventory slots in the inventory.
     */
    @Getter private final int size;

    /** The actual slots of the inventory. */
    private final InventorySlot[] slots;

    /**
     * Create a new inventory of a given size.
     *
     * @param size The number of slots the inventory contains.
     * @throws IllegalArgumentException If the size is less than or equal to 0.
     */
    public Inventory(final int size) {
        if (size <= 0) {
            String error =
                    SafeResourceLoader.getString(
                            "INVALID_INVENTORY_SIZE", FactoryPlugin.getResourceBundle());
            log.warn(error);
            throw new IllegalArgumentException(error);
        }
        this.size = size;
        slots = new InventorySlot[size];
        for (int i = 0; i < size; ++i) {
            slots[i] = new InventorySlot();
        }
    }

    /**
     * Insert an item into the inventory.
     *
     * @param item The item to insert.
     * @return Whether we could fit the item.
     */
    public boolean addItem(@NonNull Item item) {
        for (int i = 0; i < size; ++i) {
            InventorySlot slot = slots[i];
            if (slot.isEmpty()) {
                ItemStack stack = new ItemStack(item, 1);
                return slot.setItemStack(stack);
            }

            Item stackItem = slot.getItem();
            if (!Item.isSameType(stackItem, item)) {
                // Different items
                continue;
            }

            ItemStack slotStack = slot.getItemStack();
            if (slotStack.getCount() < ItemStack.MAX_STACK_SIZE) {
                slotStack.setCount(slotStack.getCount() + 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Add a stack of items to the inventory. It will add to the existing stacks of the same item if
     * possible, then the remainder will go into an empty slot.
     *
     * @param item The item to insert.
     * @param count How many of the items we want to add. If less than or equal to 0, nothing
     *     happens, and will be reduced to the max stack size if larger.
     * @return Whether we could add the items completely.
     */
    public boolean addItem(@NonNull Item item, final int count) {
        if (count <= 0) {
            return false;
        }
        ItemStack stack = new ItemStack(item, Math.min(count, ItemStack.MAX_STACK_SIZE));
        if (!addToExistingStacks(stack)) {
            // There is a remainder
            return addToEmptySlot(stack);
        }
        // We added it without any leftovers
        return true;
    }

    /**
     * Place a stack into the first empty slot we can find.
     *
     * @param stack The stack to store.
     * @return Whether if we found a spot.
     */
    private boolean addToEmptySlot(@NonNull ItemStack stack) {
        for (int i = 0; i < size; ++i) {
            InventorySlot slot = slots[i];
            if (slot.isEmpty()) {
                return slot.setItemStack(stack);
            }
        }
        return false;
    }

    /**
     * Add the item stack to existing stacks of the same type. The stack will be reduced by the
     * amount we can fit into other stacks already in the inventory, and will ideally end up empty
     * if there is room.
     *
     * <p>This will NOT insert into empty slots, the intent is to use it to fill up existing stacks
     * first, then place the remainder into an empty slot if possible.
     *
     * @param stack The stack to add to existing slots containing similar items.
     * @return Whether we fully emptied the stack. If false, the input stack will still have items
     *     in it.
     */
    private boolean addToExistingStacks(@NonNull ItemStack stack) {
        for (int i = 0; i < size; ++i) {
            InventorySlot slot = slots[i];
            if (slot.isEmpty() || !ItemStack.isSameType(stack, slot.getItemStack())) {
                continue;
            }

            ItemStack slotStack = slot.getItemStack();

            final int itemsToTransfer =
                    Math.min(stack.getCount(), ItemStack.MAX_STACK_SIZE - slotStack.getCount());

            slotStack.setCount(slotStack.getCount() + itemsToTransfer);
            stack.setCount(stack.getCount() - itemsToTransfer);
            if (stack.getCount() <= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the two slots both have items of the same type.
     *
     * @param first The first slot.
     * @param second The second slot.
     * @return Whether the slots could stack together, without taking into account stack size.
     */
    public boolean isSameType(int first, int second) {
        if (first < 0 || first >= size || second < 0 || second >= size) {
            return false;
        }

        if (first == second) {
            return true;
        }

        InventorySlot firstSlot = slots[first];
        InventorySlot secondSlot = slots[second];

        return ItemStack.isSameType(firstSlot.getStack(), secondSlot.getStack());
    }

    /**
     * Checks if the inventory has room to fit the given item. If it's not stackable it will only
     * fit in an empty slot, but if stackable we also check if there is a stack of the same item
     * that can fit it.
     *
     * @param item The item we are looking to insert.
     * @return Whether the inventory has room to fit the given item.
     */
    public boolean canFitItem(@NonNull Item item) {
        for (int i = 0; i < size; ++i) {
            InventorySlot slot = slots[i];
            if (slot.isEmpty()) {
                return true;
            }

            Item stackItem = slot.getItem();

            if (!Item.isSameType(stackItem, item)) {
                // Different items
                continue;
            }

            if (slot.getItemStack().getCount() < ItemStack.MAX_STACK_SIZE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the inventory has room to fit the given item. If it's not stackable it will only
     * fit in an empty slot, but if stackable we also check if there is a stack of the same item
     * that can fit it.
     *
     * @param item The item we are looking to insert.
     * @param amount How many of the item we want to add.
     * @return Whether the inventory has room to fit the given item. False if amount &lt;= 0.
     */
    public boolean canFitItem(@NonNull Item item, int amount) {
        if (amount <= 0) {
            return false;
        }
        int remainingAmount = amount;
        for (int i = 0; i < size; ++i) {
            InventorySlot slot = slots[i];
            if (!slot.isEmpty() && !Item.isSameType(item, slot.getItem())) {
                continue;
            }

            ItemStack stack = slot.getItemStack();
            int amountInSlot = stack != null ? stack.getCount() : 0;
            int roomInSlot = ItemStack.MAX_STACK_SIZE - amountInSlot;
            if (roomInSlot >= remainingAmount) {
                return true;
            }
            remainingAmount -= roomInSlot;
        }
        return remainingAmount <= 0;
    }

    /**
     * Clear the slot at the given index, rendering it empty.
     *
     * @param slotNumber The slot number to clear.
     */
    public void clearSlot(int slotNumber) {
        if (slotNumber < 0 || slotNumber >= size) {
            return;
        }
        slots[slotNumber].clear();
    }

    /**
     * Combine two slots, both of which must contain stackable items that can stack together or else
     * nothing will happen.
     *
     * @param source The source slot.
     * @param destination The destination slot.
     * @return Whether the source slot is now empty.
     */
    public boolean combineSlots(int source, int destination) {
        if (source < 0
                || source >= size
                || destination < 0
                || destination >= size
                || source == destination) {
            return false;
        }
        if (InventorySlot.combine(slots[source], slots[destination])) {
            slots[source].clear();
            return true;
        }
        return false;
    }

    /**
     * Fetch the item in a given slot. Invalid slots are considered empty.
     *
     * @param slotNumber The slot number to fetch.
     * @return The item in that slot, or an empty optional if there is none.
     */
    public Optional<Item> getItem(int slotNumber) {
        if (slotNumber < 0 || slotNumber >= size) {
            return Optional.empty();
        }
        return Optional.ofNullable(slots[slotNumber].getItem());
    }

    /**
     * Fetch the number of items in a given slot. Invalid slot numbers are considered 0.
     *
     * @param slotNumber The slot to inspect.
     * @return The number of items in the given slot number.
     */
    public int getItemCount(int slotNumber) {
        if (slotNumber < 0 || slotNumber >= size) {
            return 0;
        }
        return slots[slotNumber].getCount();
    }

    /**
     * Whether there is an empty slot in the inventory.
     *
     * @return Whether there is an empty slot.
     */
    public boolean hasEmptySlot() {
        for (int i = 0; i < size; ++i) {
            InventorySlot slot = slots[i];
            if (slot.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience method for {@code !isEmpty(slotNumber)}.
     *
     * @param slotNumber The inventory slot number.
     * @return Whether there is an item in the given slot.
     * @see #isEmpty(int)
     */
    public boolean hasItem(int slotNumber) {
        return !this.isEmpty(slotNumber);
    }

    /**
     * Check if a slot is empty. Invalid slot numbers are considered empty, as that's easier than
     * throwing exceptions.
     *
     * @param slotNumber The inventory slot number.
     * @return Whether the slot number is empty.
     * @see #hasItem(int)
     */
    public boolean isEmpty(int slotNumber) {
        if (slotNumber < 0 || slotNumber >= size) {
            return true;
        }
        return slots[slotNumber].isEmpty();
    }

    /**
     * Forcibly clear and set the item in a given slot.
     *
     * @param slotNumber The slot number to fill.
     * @param stackable The stackable item.
     * @param count The number of items in the stack. If less than or equal to 0, does nothing, and
     *     will be capped at the maximum stack size for the given type of item.
     */
    public void setItem(int slotNumber, @NonNull Item stackable, int count) {
        if (slotNumber < 0 || slotNumber >= size || (count <= 0)) {
            return;
        }

        slots[slotNumber].clear();
        ItemStack stack = new ItemStack(stackable, Math.min(count, ItemStack.MAX_STACK_SIZE));
        slots[slotNumber].setItemStack(stack);
    }

    /**
     * Split the stack at the specified position, removing the specified number of items and placing
     * them into a new separate stack.
     *
     * @param slotNumber The slot we are splitting.
     * @param amountToRemove How many items to take out and put in a new stack.
     * @return Whether we could successfully split the stack.
     */
    public boolean splitStack(int slotNumber, int amountToRemove) {
        if (slotNumber < 0 || slotNumber >= size || amountToRemove <= 0) {
            return false;
        }
        InventorySlot slot = slots[slotNumber];
        if (slot.isEmpty() || !hasEmptySlot()) {
            return false;
        }
        ItemStack stack = slot.getItemStack();
        if (stack.getCount() <= amountToRemove) {
            return false;
        }
        ItemStack newStack = new ItemStack(stack.getItem(), amountToRemove);
        addToEmptySlot(newStack);
        stack.setCount(stack.getCount() - amountToRemove);
        return true;
    }

    /**
     * Swap the contents of two slots. If they are the same or either is an invalid slot number,
     * nothing happens.
     *
     * @param first The first slot.
     * @param second The second slot.
     */
    public void swapSlots(int first, int second) {
        if (first < 0 || first >= size || second < 0 || second >= size || first == second) {
            return;
        }
        InventorySlot.swapContents(slots[first], slots[second]);
    }
}
