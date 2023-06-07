package com.ikalagaming.inventory;

import com.ikalagaming.ecs.Component;
import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * An inventory that contains items.
 *
 * @author Ches Burks
 *
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class Inventory extends Component<Inventory> {
	/**
	 * The specific equipment slots that we have. The position in this enum also
	 * uniquely describes that slots position in the inventory. Be careful
	 * modifying or reordering this, or saves will break.
	 *
	 * @author Ches Burks
	 *
	 */
	public enum EquipmentSlot {
		/**
		 * Stores an amulet.
		 */
		AMULET,
		/**
		 * Stores a belt.
		 */
		BELT,
		/**
		 * Stores a cape.
		 */
		CAPE,
		/**
		 * Stores chest armor.
		 */
		CHEST,
		/**
		 * Stores foot armor.
		 */
		FEET,
		/**
		 * Stores hand armor.
		 */
		HANDS,
		/**
		 * Stores head armor.
		 */
		HEAD,
		/**
		 * Stores leg armor.
		 */
		LEGS,
		/**
		 * Stores a one-handed or two-handed weapon. Two-handed weapons would
		 * prevent use of the off-hand.
		 */
		MAIN_HAND,
		/**
		 * Stores one-handed weapons, shields, orbs, etc.
		 */
		OFF_HAND,
		/**
		 * Stores a ring.
		 */
		RING_LEFT,
		/**
		 * Stores a ring.
		 */
		RING_RIGHT,
		/**
		 * Stores shoulder armor.
		 */
		SHOULDERS,
		/**
		 * Stores trinkets.
		 */
		TRINKET,
		/**
		 * Stores wrist armor.
		 */
		WRIST;
	}

	/**
	 * Swap slots between inventories. If invalid or the same exact slots are
	 * provided, nothing happens.
	 *
	 * @param firstInventory The first inventory.
	 * @param firstSlot The slot within the first inventory.
	 * @param secondInventory The second inventory.
	 * @param secondSlot The slot within the second inventory.
	 */
	public static void swapSlots(@NonNull Inventory firstInventory,
		@NonNull EquipmentSlot firstSlot, @NonNull Inventory secondInventory,
		@NonNull EquipmentSlot secondSlot) {
		if (firstInventory == secondInventory && firstSlot.equals(secondSlot)) {
			return;
		}
		InventorySlot.swapContents(
			firstInventory.equipmentSlots[firstSlot.ordinal()],
			secondInventory.equipmentSlots[secondSlot.ordinal()]);
	}

	/**
	 * Swap slots between inventories. If invalid slots are provided, nothing
	 * happens.
	 *
	 * @param firstInventory The first inventory.
	 * @param firstSlot The equipment slot within the first inventory.
	 * @param secondInventory The second inventory.
	 * @param secondSlot The slot within the second inventory.
	 */
	public static void swapSlots(@NonNull Inventory firstInventory,
		@NonNull EquipmentSlot firstSlot, @NonNull Inventory secondInventory,
		int secondSlot) {
		// This is a convenience method, just call the other one.
		Inventory.swapSlots(secondInventory, secondSlot, firstInventory,
			firstSlot);
	}

	/**
	 * Swap slots between inventories. If invalid slots are provided, nothing
	 * happens.
	 *
	 * @param firstInventory The first inventory.
	 * @param firstSlot The slot within the first inventory.
	 * @param secondInventory The second inventory.
	 * @param secondSlot The equipment slot within the second inventory.
	 */
	public static void swapSlots(@NonNull Inventory firstInventory,
		int firstSlot, @NonNull Inventory secondInventory,
		@NonNull EquipmentSlot secondSlot) {
		if (firstSlot < 0 || firstSlot >= firstInventory.size) {
			return;
		}
		InventorySlot.swapContents(firstInventory.slots[firstSlot],
			secondInventory.equipmentSlots[secondSlot.ordinal()]);
	}

	/**
	 * Swap slots between inventories. If they are the same inventory and slot,
	 * or either is an invalid slot number, nothing happens.
	 *
	 * @param firstInventory The first inventory.
	 * @param firstSlot The slot within the first inventory.
	 * @param secondInventory The second inventory.
	 * @param secondSlot The slot within the second inventory.
	 */
	public static void swapSlots(@NonNull Inventory firstInventory,
		int firstSlot, @NonNull Inventory secondInventory, int secondSlot) {
		if (firstSlot < 0 || firstSlot >= firstInventory.size || secondSlot < 0
			|| secondSlot >= secondInventory.size
			|| (firstInventory == secondInventory && firstSlot == secondSlot)) {
			return;
		}
		InventorySlot.swapContents(firstInventory.slots[firstSlot],
			secondInventory.slots[secondSlot]);
	}

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
	 * Slots to store equipment.
	 */
	private InventorySlot[] equipmentSlots;

	/**
	 * Create a new inventory of a given size.
	 *
	 * @param size The number of slots the inventory contains.
	 * @throws IllegalArgumentException If the size is less than or equal to 0.
	 */
	public Inventory(final int size) {
		if (size <= 0) {
			String error = SafeResourceLoader.getString(
				"INVALID_INVENTORY_SIZE", InventoryPlugin.getResourceBundle());
			Inventory.log.warn(error);
			throw new IllegalArgumentException(error);
		}
		this.size = size;
		this.slots = new InventorySlot[size];
		for (int i = 0; i < size; ++i) {
			this.slots[i] = new InventorySlot();
		}
		this.equipmentSlots = new InventorySlot[EquipmentSlot.values().length];
		for (int i = 0; i < this.equipmentSlots.length; ++i) {
			this.equipmentSlots[i] = new InventorySlot();
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

			Item stackItem = slot.getItem();
			if (!stackItem.getID().equals(item.getID())) {
				// Different items
				continue;
			}

			int maxStack = InvUtil.maxStackSize(stackItem);
			ItemStack slotStack = slot.getItemStack();
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
	 * @param item The item to insert.
	 * @param count How many of the items we want to add. If less than or equal
	 *            to 0, nothing happens, and will be reduced to the max stack
	 *            size if larger.
	 * @return Whether we could add the items completely.
	 */
	public boolean addItem(@NonNull Item item, final int count) {
		if (count <= 0) {
			return false;
		}
		if (!InvUtil.canStack(item)) {
			return this.addItem(item);
		}
		ItemStack stack =
			new ItemStack(item, Math.min(count, InvUtil.maxStackSize(item)));
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
				|| !ItemStack.isSameType(stack, slot.getItemStack())) {
				continue;
			}

			ItemStack slotStack = slot.getItemStack();

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
		if (first < 0 || first >= this.size || second < 0
			|| second >= this.size) {
			return false;
		}

		InventorySlot firstSlot = this.slots[first];
		if (first == second) {
			return firstSlot.isStackable();
		}
		InventorySlot secondSlot = this.slots[second];

		if (firstSlot.isEmpty() || secondSlot.isEmpty()
			|| !firstSlot.isStackable() || !secondSlot.isStackable()) {
			return false;
		}

		String firstID = firstSlot.getItem().getID();
		String secondID = secondSlot.getItem().getID();

		return firstID.equals(secondID);
	}

	/**
	 * Check if we can store the item in the equipment inventory slots.
	 *
	 * @param item The item to store.
	 * @return Whether the appropriate slot is empty.
	 */
	public boolean canEquip(@NonNull Item item) {
		switch (item.getItemType()) {
			case ACCESSORY:
				Accessory accessory = (Accessory) item;
				switch (accessory.getAccessoryType()) {
					case AMULET:
						return this.isEmpty(EquipmentSlot.AMULET);
					case BELT:
						return this.isEmpty(EquipmentSlot.BELT);
					case CAPE:
						return this.isEmpty(EquipmentSlot.CAPE);
					case RING:
						return this.isEmpty(EquipmentSlot.RING_LEFT)
							|| this.isEmpty(EquipmentSlot.RING_RIGHT);
					case TRINKET:
						return this.isEmpty(EquipmentSlot.TRINKET);
					default:
						return false;
				}
			case ARMOR:
				Armor armor = (Armor) item;
				switch (armor.getArmorType()) {
					case CHEST:
						return this.isEmpty(EquipmentSlot.CHEST);
					case FEET:
						return this.isEmpty(EquipmentSlot.FEET);
					case HANDS:
						return this.isEmpty(EquipmentSlot.HANDS);
					case HEAD:
						return this.isEmpty(EquipmentSlot.HEAD);
					case LEGS:
						return this.isEmpty(EquipmentSlot.LEGS);
					case SHOULDERS:
						return this.isEmpty(EquipmentSlot.SHOULDERS);
					case WRIST:
						return this.isEmpty(EquipmentSlot.WRIST);
					default:
						return false;
				}
			case WEAPON:
				Weapon weapon = (Weapon) item;
				switch (weapon.getWeaponType()) {
					case OFF_HAND, SHIELD:
						return this.isEmpty(EquipmentSlot.OFF_HAND)
							&& !this.holdingTwoHanded();
					case ONE_HANDED_MAGIC, ONE_HANDED_MELEE, ONE_HANDED_RANGED:
						return this.isEmpty(EquipmentSlot.MAIN_HAND)
							|| (this.isEmpty(EquipmentSlot.OFF_HAND)
								&& !this.holdingTwoHanded());
					case TWO_HANDED_MAGIC, TWO_HANDED_MELEE, TWO_HANDED_RANGED:
						return this.isEmpty(EquipmentSlot.MAIN_HAND)
							&& this.isEmpty(EquipmentSlot.OFF_HAND);
					default:
						return false;
				}
			case COMPONENT, CONSUMABLE, JUNK, MATERIAL, QUEST:
			default:
				return false;
		}
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
			Item stackItem = slot.getItem();
			if (!stackItem.getID().equals(item.getID())) {
				// Different items
				continue;
			}
			int maxStack = InvUtil.maxStackSize(stackItem);
			if (slot.getItemStack().getCount() < maxStack) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the inventory has room to fit the given item. If it's not
	 * stackable it will only fit in an empty slot, but if stackable we also
	 * check if there is a stack of the same item that can fit it.
	 *
	 * @param item The item we are looking to insert.
	 * @param amount How many of the item we want to add.
	 * @return Whether the inventory has room to fit the given item.
	 */
	public boolean canFitItem(@NonNull Item item, int amount) {
		// If we straight up don't have enough slots in the inventory, bail
		if ((amount <= 0) || (!InvUtil.canStack(item) && amount > this.size)) {
			return false;
		}
		final int maxStackSize = InvUtil.maxStackSize(item);
		int remainingAmount = amount;
		for (int i = 0; i < this.size; ++i) {
			InventorySlot slot = this.slots[i];
			if (!(slot.isEmpty() || (slot.isStackable()
				&& item.getID().equals(slot.getItem().getID())))) {
				/*
				 * If the slot is filled, and it's either unstackable or
				 * different, we keep going.
				 */
				continue;
			}

			ItemStack stack = slot.getItemStack();
			int amountInSlot = stack != null ? stack.getCount() : 0;
			int roomInSlot = maxStackSize - amountInSlot;
			if (roomInSlot >= remainingAmount) {
				return true;
			}
			if (roomInSlot > 0) {
				remainingAmount -= roomInSlot;
			}
		}
		return remainingAmount <= 0;
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
		if (source < 0 || source >= this.size || destination < 0
			|| destination >= this.size || source == destination) {
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
	 * Stores the item in the appropriate equipment slot, unless there is
	 * already something there or it's not something we can equip.
	 *
	 * @param item The item to store.
	 */
	public void equipItem(@NonNull Item item) {
		if (!this.canFitItem(item)) {
			return;
		}
		switch (item.getItemType()) {
			case ACCESSORY:
				Accessory accessory = (Accessory) item;
				switch (accessory.getAccessoryType()) {
					case AMULET:
						this.setItem(EquipmentSlot.AMULET, accessory);
						return;
					case BELT:
						this.setItem(EquipmentSlot.BELT, accessory);
						return;
					case CAPE:
						this.setItem(EquipmentSlot.CAPE, accessory);
						return;
					case RING:
						if (this.isEmpty(EquipmentSlot.RING_LEFT)) {
							this.setItem(EquipmentSlot.RING_LEFT, accessory);
							return;
						}
						this.setItem(EquipmentSlot.RING_RIGHT, accessory);
						return;
					case TRINKET:
						this.setItem(EquipmentSlot.TRINKET, accessory);
						return;
					default:
						return;
				}
			case ARMOR:
				Armor armor = (Armor) item;
				switch (armor.getArmorType()) {
					case CHEST:
						this.setItem(EquipmentSlot.CHEST, armor);
						return;
					case FEET:
						this.setItem(EquipmentSlot.FEET, armor);
						return;
					case HANDS:
						this.setItem(EquipmentSlot.HANDS, armor);
						return;
					case HEAD:
						this.setItem(EquipmentSlot.HEAD, armor);
						return;
					case LEGS:
						this.setItem(EquipmentSlot.LEGS, armor);
						return;
					case SHOULDERS:
						this.setItem(EquipmentSlot.SHOULDERS, armor);
						return;
					case WRIST:
						this.setItem(EquipmentSlot.WRIST, armor);
						return;
					default:
						return;
				}
			case WEAPON:
				Weapon weapon = (Weapon) item;
				switch (weapon.getWeaponType()) {
					case OFF_HAND, SHIELD:
						this.setItem(EquipmentSlot.OFF_HAND, weapon);
						return;
					case ONE_HANDED_MAGIC, ONE_HANDED_MELEE, ONE_HANDED_RANGED:
						if (this.isEmpty(EquipmentSlot.MAIN_HAND)) {
							this.setItem(EquipmentSlot.MAIN_HAND, weapon);
							return;
						}
						this.setItem(EquipmentSlot.OFF_HAND, weapon);
						return;
					case TWO_HANDED_MAGIC, TWO_HANDED_MELEE, TWO_HANDED_RANGED:
						this.setItem(EquipmentSlot.MAIN_HAND, weapon);
						return;
					default:
						return;
				}
			case COMPONENT, CONSUMABLE, JUNK, MATERIAL, QUEST:
			default:
		}
	}

	/**
	 * Fetch the item in a given slot.
	 *
	 * @param slot The equipment slot.
	 *
	 * @return The item in that slot, or an empty optional if there is none.
	 */
	public Optional<Item> getItem(@NonNull EquipmentSlot slot) {
		return Optional
			.ofNullable(this.equipmentSlots[slot.ordinal()].getItem());
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
		return Optional.ofNullable(this.slots[slotNumber].getItem());
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
	 * Check if there is a two handed weapon being held. These are held in the
	 * main hand but prevent holding anything in the off hand.
	 *
	 * @return Whether a two handed weapon is being held.
	 */
	private boolean holdingTwoHanded() {
		InventorySlot slot =
			this.equipmentSlots[EquipmentSlot.MAIN_HAND.ordinal()];
		if (slot.isEmpty()) {
			return false;
		}

		Weapon main = (Weapon) slot.getItem();
		switch (main.getWeaponType()) {
			case TWO_HANDED_MAGIC, TWO_HANDED_MELEE, TWO_HANDED_RANGED:
				return true;
			case OFF_HAND, ONE_HANDED_MAGIC, ONE_HANDED_MELEE,
				ONE_HANDED_RANGED, SHIELD:
			default:
				return false;
		}
	}

	/**
	 * Check if a slot is empty. Invalid slot numbers are considered empty, as
	 * that's easier than throwing exceptions.
	 *
	 * @param slot The equipment slot.
	 * @return Whether the slot number is empty.
	 */
	private boolean isEmpty(@NonNull EquipmentSlot slot) {
		return this.equipmentSlots[slot.ordinal()].isEmpty();
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
	 * @param slot The equipment slot.
	 * @param nonStackable The stackable item.
	 */
	private void setItem(@NonNull EquipmentSlot slot,
		@NonNull Equipment nonStackable) {
		this.equipmentSlots[slot.ordinal()].clear();
		this.equipmentSlots[slot.ordinal()].setItem(nonStackable);
	}

	/**
	 * Forcibly clear and set the item in a given slot.
	 *
	 * @param slotNumber The slot number to fill.
	 * @param nonStackable The stackable item.
	 * @see #setItem(int, Item, int)
	 */
	public void setItem(int slotNumber, @NonNull Equipment nonStackable) {
		if (slotNumber < 0 || slotNumber >= this.size) {
			return;
		}
		this.slots[slotNumber].clear();
		this.slots[slotNumber].setItem(nonStackable);
	}

	/**
	 * Forcibly clear and set the item in a given slot. If not stackable, we set
	 * the item using {@link #setItem(int, Equipment)} instead.
	 *
	 * @param slotNumber The slot number to fill.
	 * @param stackable The stackable item.
	 * @param count The number of items in the stack. If less than or equal to
	 *            0, does nothing, and will be capped at the maximum stack size
	 *            for the given type of item.
	 * @see #setItem(int, Equipment)
	 */
	public void setItem(int slotNumber, @NonNull Item stackable, int count) {
		if (slotNumber < 0 || slotNumber >= this.size || (count <= 0)) {
			return;
		}
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
		if (slotNumber < 0 || slotNumber >= this.size || amountToRemove <= 0) {
			return false;
		}
		InventorySlot slot = this.slots[slotNumber];
		if (slot.isEmpty() || !slot.isStackable() || !this.hasEmptySlot()) {
			return false;
		}
		ItemStack stack = slot.getItemStack();
		if (stack.getCount() <= amountToRemove) {
			return false;
		}
		ItemStack newStack = new ItemStack(stack.getItem(), amountToRemove);
		this.addToEmptySlot(newStack);
		stack.setCount(stack.getCount() - amountToRemove);
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
		if (first < 0 || first >= this.size || second < 0 || second >= this.size
			|| first == second) {
			return;
		}
		InventorySlot.swapContents(this.slots[first], this.slots[second]);
	}
}
