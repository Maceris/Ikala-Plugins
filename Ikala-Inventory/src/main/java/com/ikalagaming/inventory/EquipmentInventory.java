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
public class EquipmentInventory extends Inventory {

	/**
	 * The specific slots that we have. The position in this enum also uniquely
	 * describes that slots position in the inventory. Be careful modifying or
	 * reordering this, or saves will break.
	 *
	 * @author Ches Burks
	 *
	 */
	public enum Slots {
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
	 * Construct a new equipment inventory.
	 */
	public EquipmentInventory() {
		super(Slots.values().length);
	}

	/**
	 * Check if we can store the item in the inventory.
	 *
	 * @param item The item to store.
	 * @return Whether the appropriate slot is empty.
	 */
	@Override
	public boolean canFitItem(@NonNull Item item) {
		switch (item.getItemType()) {
			case ACCESSORY:
				Accessory accessory = (Accessory) item;
				switch (accessory.getAccessoryType()) {
					case AMULET:
						return this.isEmpty(Slots.AMULET.ordinal());
					case BELT:
						return this.isEmpty(Slots.BELT.ordinal());
					case CAPE:
						return this.isEmpty(Slots.CAPE.ordinal());
					case RING:
						return this.isEmpty(Slots.RING_LEFT.ordinal())
							|| this.isEmpty(Slots.RING_RIGHT.ordinal());
					case TRINKET:
						return this.isEmpty(Slots.TRINKET.ordinal());
					default:
						return false;
				}
			case ARMOR:
				Armor armor = (Armor) item;
				switch (armor.getArmorType()) {
					case CHEST:
						return this.isEmpty(Slots.CHEST.ordinal());
					case FEET:
						return this.isEmpty(Slots.FEET.ordinal());
					case HANDS:
						return this.isEmpty(Slots.HANDS.ordinal());
					case HEAD:
						return this.isEmpty(Slots.HEAD.ordinal());
					case LEGS:
						return this.isEmpty(Slots.LEGS.ordinal());
					case SHOULDERS:
						return this.isEmpty(Slots.SHOULDERS.ordinal());
					case WRIST:
						return this.isEmpty(Slots.WRIST.ordinal());
					default:
						return false;
				}
			case WEAPON:
				Weapon weapon = (Weapon) item;
				switch (weapon.getWeaponType()) {
					case OFF_HAND:
					case SHIELD:
						return this.isEmpty(Slots.OFF_HAND.ordinal())
							&& !this.holdingTwoHanded();
					case ONE_HANDED_MAGIC:
					case ONE_HANDED_MELEE:
					case ONE_HANDED_RANGED:
						return this.isEmpty(Slots.MAIN_HAND.ordinal())
							|| (this.isEmpty(Slots.OFF_HAND.ordinal())
								&& !this.holdingTwoHanded());
					case TWO_HANDED_MAGIC:
					case TWO_HANDED_MELEE:
					case TWO_HANDED_RANGED:
						return this.isEmpty(Slots.MAIN_HAND.ordinal())
							&& this.isEmpty(Slots.OFF_HAND.ordinal());
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
		if (this.isEmpty(Slots.MAIN_HAND.ordinal())) {
			return false;
		}

		Weapon main = (Weapon) this.getItem(Slots.MAIN_HAND.ordinal()).get();
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
	 * Store the item in the inventory. If it can't fit, it won't be stored
	 * there.
	 *
	 * @param item The item to store.
	 */
	public void storeItem(@NonNull Item item) {
		if (!this.canFitItem(item)) {
			return;
		}
		switch (item.getItemType()) {
			case ACCESSORY:
				Accessory accessory = (Accessory) item;
				switch (accessory.getAccessoryType()) {
					case AMULET:
						this.setItem(Slots.AMULET.ordinal(), accessory);
					case BELT:
						this.setItem(Slots.BELT.ordinal(), accessory);
					case CAPE:
						this.setItem(Slots.CAPE.ordinal(), accessory);
					case RING:
						if (this.isEmpty(Slots.RING_LEFT.ordinal())) {
							this.setItem(Slots.RING_LEFT.ordinal(), accessory);
						}
						this.setItem(Slots.RING_RIGHT.ordinal(), accessory);
					case TRINKET:
						this.setItem(Slots.TRINKET.ordinal(), accessory);
					default:
						return;
				}
			case ARMOR:
				Armor armor = (Armor) item;
				switch (armor.getArmorType()) {
					case CHEST:
						this.setItem(Slots.CHEST.ordinal(), armor);
					case FEET:
						this.setItem(Slots.FEET.ordinal(), armor);
					case HANDS:
						this.setItem(Slots.HANDS.ordinal(), armor);
					case HEAD:
						this.setItem(Slots.HEAD.ordinal(), armor);
					case LEGS:
						this.setItem(Slots.LEGS.ordinal(), armor);
					case SHOULDERS:
						this.setItem(Slots.SHOULDERS.ordinal(), armor);
					case WRIST:
						this.setItem(Slots.WRIST.ordinal(), armor);
					default:
						return;
				}
			case WEAPON:
				Weapon weapon = (Weapon) item;
				switch (weapon.getWeaponType()) {
					case OFF_HAND:
					case SHIELD:
						this.setItem(Slots.OFF_HAND.ordinal(), weapon);
					case ONE_HANDED_MAGIC:
					case ONE_HANDED_MELEE:
					case ONE_HANDED_RANGED:
						if (this.isEmpty(Slots.MAIN_HAND.ordinal())) {
							this.setItem(Slots.MAIN_HAND.ordinal(), weapon);
						}
						this.setItem(Slots.OFF_HAND.ordinal(), weapon);
					case TWO_HANDED_MAGIC:
					case TWO_HANDED_MELEE:
					case TWO_HANDED_RANGED:
						this.setItem(Slots.MAIN_HAND.ordinal(), weapon);
					default:
						return;
				}
			case COMPONENT:
			case CONSUMABLE:
			case JUNK:
			case MATERIAL:
			case QUEST:
			default:
		}
	}

}
