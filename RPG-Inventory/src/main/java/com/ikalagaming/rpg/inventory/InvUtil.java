package com.ikalagaming.rpg.inventory;

import com.ikalagaming.rpg.item.Equipment;
import com.ikalagaming.rpg.item.Item;

/**
 * Utility and business logic for inventories.
 *
 * @author Ches Burks
 *
 */
public class InvUtil {
	/**
	 * Checks if an item can stack based on what kind of item it is. Equipment
	 * (weapons, armor, etc.) are always unique and so can't stack, but pretty
	 * much everything else can.
	 *
	 * @param item The item we are checking.
	 * @return Whether the item should be able to stack.
	 */
	public static boolean canStack(Item item) {
		return !(item instanceof Equipment);
	}

	/**
	 * Return the maximum stack size for an item, based on what kind of item it
	 * is.
	 *
	 * @param item The item we are interested in.
	 * @return The maximum stack size of that type of item.
	 */
	public static int maxStackSize(Item item) {
		switch (item.getItemType()) {
			case COMPONENT, CONSUMABLE, JUNK, MATERIAL, QUEST:
				return 64;
			case ACCESSORY, ARMOR, WEAPON:
			default:
				return 1;
		}
	}

	/**
	 * Private constructor so this class is not instantiated.
	 */
	private InvUtil() {
		throw new UnsupportedOperationException(
			"This utility class should not be instantiated");
	}
}
