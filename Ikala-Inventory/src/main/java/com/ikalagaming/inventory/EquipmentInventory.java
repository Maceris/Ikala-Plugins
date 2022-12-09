package com.ikalagaming.inventory;

import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.Weapon;

import lombok.NonNull;

/**
 * The "body slots" for an entity, where armor, accessories, and weapons are
 * stored when in use.
 * 
 * @author Ches Burks
 *
 */
public class EquipmentInventory {
	private InventorySlot chest;
	private InventorySlot feet;
	private InventorySlot hands;
	private InventorySlot head;
	private InventorySlot legs;
	private InventorySlot shoulders;
	private InventorySlot wrist;
	private InventorySlot amulet;
	private InventorySlot belt;
	private InventorySlot cape;
	private InventorySlot ringLeft;
	private InventorySlot ringRight;
	private InventorySlot trinket;
	private InventorySlot mainHand;
	private InventorySlot offHand;

	/**
	 * Construct a new equipment inventory.
	 */
	public EquipmentInventory() {
		this.chest = new InventorySlot();
		this.feet = new InventorySlot();
		this.hands = new InventorySlot();
		this.head = new InventorySlot();
		this.legs = new InventorySlot();
		this.shoulders = new InventorySlot();
		this.wrist = new InventorySlot();
		this.amulet = new InventorySlot();
		this.belt = new InventorySlot();
		this.cape = new InventorySlot();
		this.ringLeft = new InventorySlot();
		this.ringRight = new InventorySlot();
		this.trinket = new InventorySlot();
		this.mainHand = new InventorySlot();
		this.offHand = new InventorySlot();
	}

	/**
	 * Check if we can store the item in the inventory.
	 *
	 * @param item The item to store.
	 * @return Whether the appropriate slot is empty.
	 */
	public boolean canFitItem(@NonNull Item item) {
		switch (item.getItemType()) {
			case ACCESSORY:
				Accessory accessory = (Accessory) item;
				switch (accessory.getAccessoryType()) {
					case AMULET:
						return this.amulet.isEmpty();
					case BELT:
						return this.belt.isEmpty();
					case CAPE:
						return this.cape.isEmpty();
					case RING:
						return this.ringLeft.isEmpty()
							|| this.ringRight.isEmpty();
					case TRINKET:
						return this.trinket.isEmpty();
					default:
						return false;
				}
			case ARMOR:
				Armor armor = (Armor) item;
				switch (armor.getArmorType()) {
					case CHEST:
						return this.chest.isEmpty();
					case FEET:
						return this.feet.isEmpty();
					case HANDS:
						return this.hands.isEmpty();
					case HEAD:
						return this.head.isEmpty();
					case LEGS:
						return this.legs.isEmpty();
					case SHOULDERS:
						return this.shoulders.isEmpty();
					case WRIST:
						return this.wrist.isEmpty();
					default:
						return false;
				}
			case WEAPON:
				Weapon weapon = (Weapon) item;
				switch (weapon.getWeaponType()) {
					case OFF_HAND:
					case SHIELD:
						return this.offHand.isEmpty()
							&& !this.holdingTwoHanded();
					case ONE_HANDED_MAGIC:
					case ONE_HANDED_MELEE:
					case ONE_HANDED_RANGED:
						return this.mainHand.isEmpty()
							|| (this.offHand.isEmpty()
								&& !this.holdingTwoHanded());
					case TWO_HANDED_MAGIC:
					case TWO_HANDED_MELEE:
					case TWO_HANDED_RANGED:
						return this.mainHand.isEmpty()
							&& this.offHand.isEmpty();
					default:
						return false;
				}
			case COMPONENT:
			case CONSUMABLE:
			case JUNK:
			case MATERIAL:
			case QUEST:
			default:
				return false;
		}
	}

	/**
	 * Check if there is a two handed weapon being held. These are held in the
	 * main hand but prevent holding anything in the off hand.
	 *
	 * @return Whether a two handed weapon is being held.
	 */
	private boolean holdingTwoHanded() {
		if (this.mainHand.isEmpty()) {
			return false;
		}

		Weapon main = (Weapon) this.mainHand.getItem().get();
		switch (main.getWeaponType()) {
			case OFF_HAND:
			case ONE_HANDED_MAGIC:
			case ONE_HANDED_MELEE:
			case ONE_HANDED_RANGED:
			case SHIELD:
			default:
				return false;
			case TWO_HANDED_MAGIC:
			case TWO_HANDED_MELEE:
			case TWO_HANDED_RANGED:
				return true;
		}
	}

	/**
	 * Check if we can store the item in the inventory.
	 *
	 * @param item The item to store.
	 * @return Whether the appropriate slot is empty.
	 */
	public boolean storeItem(@NonNull Item item) {
		if (!this.canFitItem(item)) {
			return false;
		}
		switch (item.getItemType()) {
			case ACCESSORY:
				Accessory accessory = (Accessory) item;
				switch (accessory.getAccessoryType()) {
					case AMULET:
						return this.amulet.setItem(accessory);
					case BELT:
						return this.belt.setItem(accessory);
					case CAPE:
						return this.cape.setItem(accessory);
					case RING:
						if (this.ringLeft.isEmpty()) {
							return this.ringLeft.setItem(accessory);
						}
						return this.ringRight.setItem(accessory);
					case TRINKET:
						return this.trinket.setItem(accessory);
					default:
						return false;
				}
			case ARMOR:
				Armor armor = (Armor) item;
				switch (armor.getArmorType()) {
					case CHEST:
						return this.chest.setItem(armor);
					case FEET:
						return this.feet.setItem(armor);
					case HANDS:
						return this.hands.setItem(armor);
					case HEAD:
						return this.head.setItem(armor);
					case LEGS:
						return this.legs.setItem(armor);
					case SHOULDERS:
						return this.shoulders.setItem(armor);
					case WRIST:
						return this.wrist.setItem(armor);
					default:
						return false;
				}
			case WEAPON:
				Weapon weapon = (Weapon) item;
				switch (weapon.getWeaponType()) {
					case OFF_HAND:
					case SHIELD:
						return this.offHand.setItem(weapon);
					case ONE_HANDED_MAGIC:
					case ONE_HANDED_MELEE:
					case ONE_HANDED_RANGED:
						if (this.mainHand.isEmpty()) {
							return this.mainHand.setItem(weapon);
						}
						return this.offHand.setItem(weapon);
					case TWO_HANDED_MAGIC:
					case TWO_HANDED_MELEE:
					case TWO_HANDED_RANGED:
						return this.mainHand.setItem(weapon);
					default:
						return false;
				}
			case COMPONENT:
			case CONSUMABLE:
			case JUNK:
			case MATERIAL:
			case QUEST:
			default:
				return false;
		}
	}

}
