package com.ikalagaming.item;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The category of item.
 * 
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public enum ItemType {
	/**
	 * Any sort of weapon or shield, held in the hands.
	 */
	WEAPON("weapon_"),
	/**
	 * Any sort of armor, worn on the body.
	 */
	ARMOR("armor_"),
	/**
	 * Any sort of accessory that does not provide protection in the sense that
	 * armor does, worn on the body.
	 */
	ACCESSORY("accessory_"),
	/**
	 * An item that sockets into another one, to augment its stats.
	 */
	COMPONENT("component_"),
	/**
	 * An item that is consumed when used, like food or potions.
	 */
	CONSUMABLE("consumable_"),
	/**
	 * Material that is used for crafting or which has some use.
	 */
	MATERIAL("material_"),
	/**
	 * Has no use at all other than being sold for currency.
	 */
	JUNK("junk_"),
	/**
	 * Items that are required for or related to quests.
	 */
	QUEST("quest_");

	/**
	 * The prefix that is used in the IDs for items of this type in the
	 * database.
	 * 
	 * @return The prefix that appears before items of this type in the
	 *         database.
	 */
	private final String prefix;

}