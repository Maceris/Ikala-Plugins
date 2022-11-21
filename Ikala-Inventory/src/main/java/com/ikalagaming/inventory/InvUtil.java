package com.ikalagaming.inventory;

import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;

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
			case COMPONENT:
			case CONSUMABLE:
			case JUNK:
			case MATERIAL:
			case QUEST:
				return 64;
			case ACCESSORY:
			case ARMOR:
			case WEAPON:
			default:
				return 1;
		}
	}
}
