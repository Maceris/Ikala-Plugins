package com.ikalagaming.inventory;

import com.ikalagaming.ecs.Component;
import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;

import lombok.Getter;
import lombok.NonNull;

import java.util.Optional;

/**
 * An inventory that contains items.
 *
 * @author Ches Burks
 *
 */
public class Inventory extends Component<Inventory> {
	/**
	 * The size of the inventory.
	 *
	 * @return The number of inventory slots in the inventory.
	 */
	@Getter
	private final int size;

	/**
	 * The actual slots of the inventory.
	 */
	private InventorySlot[] slots;

	/**
	 * Create a new inventory of a given size.
	 *
	 * @param size The number of slots the invetory contains.
	 */
	public Inventory(final int size) {
		this.size = size;
		this.slots = new InventorySlot[size];
		for (int i = 0; i < size; ++i) {
			this.slots[i] = new InventorySlot(i);
		}
	}

	/**
	 * Insert an item into the inventory.
	 *
	 * @param item The item to insert.
	 * @return Whether we could fit the item.
	 */
	public boolean addItem(@NonNull Item item) {
		for (int i = 0; i < this.size; ++i) {
			InventorySlot slot = this.slots[i];
			if (slot.isEmpty()) {
				if (InvUtil.canStack(item)) {
					ItemStack stack = new ItemStack();
					stack.setItem(item);
					stack.setCount(1);
					slot.setItemStack(stack);
				}
				else {
					slot.setItem((Equipment) item);
				}
				return true;
			}
			if (!slot.isStackable() || !InvUtil.canStack(item)) {
				continue;
			}
			Item stackItem = slot.getItem().get();
			if (!stackItem.getID().equals(item.getID())) {
				// Different items
				continue;
			}
			int maxStack = InvUtil.maxStackSize(stackItem);
			ItemStack slotStack = slot.getItemStack().get();
			if (slotStack.getCount() < maxStack) {
				slotStack.setCount(slotStack.getCount() + 1);
				return true;
			}
		}
		return false;
	}

	/**
	 * Add a stack of items to the inventory. It will add to the existing stacks
	 * of the same item if possible, then the remainder will go into an empty
	 * slot.
	 *
	 * @param stack The stack to add, which will be reduced and ideally empty
	 *            after adding.
	 * @return Whether we could add the items completely.
	 */
	public boolean addItemStack(@NonNull ItemStack stack) {
		if (!this.addToExistingStacks(stack)) {
			// There is a remainder
			return this.addToEmptySlot(stack);
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
		for (int i = 0; i < this.size; ++i) {
			InventorySlot slot = this.slots[i];
			if (slot.isEmpty()) {
				slot.setItemStack(stack);
				return true;
			}
		}
		return false;
	}

	/**
	 * Add the item stack to existing stacks of the same type. The stack will be
	 * reduced by the amount we can fit into other stacks already in the
	 * inventory, and will ideally end up empty if there is room.
	 *
	 * This will NOT insert into empty slots, the intent is to use it to fill up
	 * existing stacks first, then place the remainder into an empty slot if
	 * possible.
	 *
	 *
	 * @param stack The stack to add to existing slots containing similar items.
	 * @return Whether we fully emptied the stack. If false, the input stack
	 *         will still have items in it.
	 */
	private boolean addToExistingStacks(@NonNull ItemStack stack) {
		final int maxStackSize = InvUtil.maxStackSize(stack.getItem());
		for (int i = 0; i < this.size; ++i) {
			InventorySlot slot = this.slots[i];
			if (slot.isEmpty() || !slot.isStackable()
				|| !ItemStack.isSameType(stack, slot.getItemStack().get())) {
				continue;
			}

			ItemStack slotStack = slot.getItemStack().get();

			final int itemsToTransfer =
				Math.min(stack.getCount(), maxStackSize - slotStack.getCount());

			slotStack.setCount(slotStack.getCount() + itemsToTransfer);
			stack.setCount(stack.getCount() - itemsToTransfer);
			if (stack.getCount() <= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the two slots both have stackable items of the same type.
	 *
	 * @param first The first slot.
	 * @param second The second slot.
	 * @return Whether the slots could stack together, without taking into
	 *         account stack size.
	 */
	public boolean areSameType(int first, int second) {
		if (first < 0 || first > this.size || second < 0 || second > this.size
			|| first == second) {
			return false;
		}

		InventorySlot firstSlot = this.slots[first];
		InventorySlot secondSlot = this.slots[second];

		if (firstSlot.isEmpty() || secondSlot.isEmpty()
			|| !firstSlot.isStackable() || !secondSlot.isStackable()) {
			return false;
		}

		if (firstSlot.getItem().isEmpty() || secondSlot.getItem().isEmpty()) {
			return false;
		}

		String firstID = firstSlot.getItem().get().getID();
		String secondID = secondSlot.getItem().get().getID();

		return firstID.equals(secondID);
	}

	/**
	 * Checks if the inventory has room to fit the given item. If it's not
	 * stackable it will only fit in an empty slot, but if stackable we also
	 * check if there is a stack of the same item that can fit it.
	 *
	 * @param item The item we are looking to insert.
	 * @return Whether the inventory has room to fit the given item.
	 */
	public boolean canFitItem(@NonNull Item item) {
		for (int i = 0; i < this.size; ++i) {
			InventorySlot slot = this.slots[i];
			if (slot.isEmpty()) {
				return true;
			}
			if (!slot.isStackable() || !InvUtil.canStack(item)) {
				continue;
			}
			Item stackItem = slot.getItem().get();
			if (!stackItem.getID().equals(item.getID())) {
				// Different items
				continue;
			}
			int maxStack = InvUtil.maxStackSize(stackItem);
			if (slot.getItemStack().get().getCount() < maxStack) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the inventory has room to fit the given item. If it's not
	 * stackable it will only fit in an empty slot, but if stackable we also
	 * check if there are stacks of the same item that can fit it.
	 *
	 * @param stack The item stack we are looking to insert.
	 * @return Whether the inventory has room to fit the given item.
	 */
	public boolean canFitItemStack(@NonNull ItemStack stack) {
		final int maxStackSize = InvUtil.maxStackSize(stack.getItem());
		int remainingAmount = stack.getCount();
		for (int i = 0; i < this.size; ++i) {
			InventorySlot slot = this.slots[i];
			if (slot.isEmpty()) {
				return true;
			}
			if (!slot.isStackable()
				|| !ItemStack.isSameType(stack, slot.getItemStack().get())) {
				continue;
			}

			int roomInSlot =
				maxStackSize - slot.getItemStack().get().getCount();
			if (roomInSlot >= remainingAmount) {
				return true;
			}
			if (roomInSlot > 0) {
				remainingAmount -= roomInSlot;
			}
		}
		return false;
	}

	/**
	 * Clear the slot at the given index, rendering it empty.
	 *
	 * @param slotNumber The slot number to clear.
	 */
	public void clearSlot(int slotNumber) {
		if (slotNumber < 0 || slotNumber >= this.size) {
			return;
		}
		this.slots[slotNumber].clear();
	}

	/**
	 * Combine two slots, both of which must contain stackable items that can
	 * stack together or else nothing will happen.
	 *
	 * @param source The source slot.
	 * @param destination The destination slot.
	 * @return Whether the source slot is now empty.
	 */
	public boolean combineSlots(int source, int destination) {
		if (source < 0 || source > this.size || destination < 0
			|| destination > this.size || source == destination) {
			return false;
		}
		if (InventorySlot.combine(this.slots[source],
			this.slots[destination])) {
			this.slots[source].clear();
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
		if (slotNumber < 0 || slotNumber >= this.size) {
			return Optional.empty();
		}
		return this.slots[slotNumber].getItem();
	}

	/**
	 * Fetch the number of items in a given slot. Invalid slot numbers are
	 * considered 0.
	 *
	 * @param slotNumber The slot to inspect.
	 * @return The number of items in the given slot number.
	 */
	public int getItemCount(int slotNumber) {
		if (slotNumber < 0 || slotNumber >= this.size) {
			return 0;
		}
		return this.slots[slotNumber].getCount();
	}

	/**
	 * Whether there is an empty slot in the inventory.
	 *
	 * @return Whether there is an empty slot.
	 */
	public boolean hasEmptySlot() {
		for (int i = 0; i < this.size; ++i) {
			InventorySlot slot = this.slots[i];
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
	 * Check if a slot is empty. Invalid slot numbers are considered empty, as
	 * that's easier than throwing exceptions.
	 *
	 * @param slotNumber The inventory slot number.
	 * @return Whether the slot number is empty.
	 * @see #hasItem(int)
	 */
	public boolean isEmpty(int slotNumber) {
		if (slotNumber < 0 || slotNumber >= this.size) {
			return true;
		}
		return this.slots[slotNumber].isEmpty();
	}

	/**
	 * Forcibly clear and set the item in a given slot.
	 *
	 * @param slotNumber The slot number to fill.
	 * @param nonStackable The stackable item.
	 * @see #setItem(int, Item, int)
	 */
	public void setItem(int slotNumber, Equipment nonStackable) {
		this.slots[slotNumber].clear();
		this.slots[slotNumber].setItem(nonStackable);
	}

	/**
	 * Forcibly clear and set the item in a given slot. If not stackable, we set
	 * the item using {@link #setItem(int, Equipment)} instead.
	 *
	 * @param slotNumber The slot number to fill.
	 * @param stackable The stackable item.
	 * @param count The number of items in the stack.
	 * @see #setItem(int, Equipment)
	 */
	public void setItem(int slotNumber, Item stackable, int count) {
		if (!InvUtil.canStack(stackable)) {
			this.setItem(slotNumber, (Equipment) stackable);
			return;
		}
		this.slots[slotNumber].clear();
		ItemStack stack = new ItemStack();
		stack.setItem(stackable);
		stack.setCount(Math.min(count, InvUtil.maxStackSize(stackable)));
		this.slots[slotNumber].setItemStack(stack);
	}

	/**
	 * Split the stack at the specified position, removing the specified number
	 * of items and placing them into a separate stack.
	 *
	 * @param slotNumber The slot we are splitting.
	 * @param amountToRemove How many items to take out and put in a new stack.
	 * @return Whether we could successfully split the stack.
	 */
	public boolean splitStack(int slotNumber, int amountToRemove) {
		if (slotNumber < 0 || slotNumber > this.size) {
			return false;
		}
		InventorySlot slot = this.slots[slotNumber];
		if (!slot.isStackable()) {
			return false;
		}
		Optional<ItemStack> maybeStack = slot.getItemStack();
		if (maybeStack.isEmpty()) {
			return false;
		}
		ItemStack oldStack = maybeStack.get();
		if (oldStack.getCount() <= amountToRemove) {
			return false;
		}

		ItemStack newStack = new ItemStack(oldStack.getItem(), amountToRemove);
		if (!this.hasEmptySlot()) {
			return false;
		}
		this.addToEmptySlot(newStack);
		oldStack.setCount(oldStack.getCount() - amountToRemove);
		return true;
	}

	/**
	 * Swap the contents of two slots. If they are the same or either is an
	 * invalid slot number, nothing happens.
	 *
	 * @param first The first slot.
	 * @param second The second slot.
	 */
	public void swapSlots(int first, int second) {
		if (first < 0 || first > this.size || second < 0 || second > this.size
			|| first == second) {
			return;
		}
		InventorySlot.swapContents(this.slots[first], this.slots[second]);
	}
}
