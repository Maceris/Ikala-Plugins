package com.ikalagaming.item;

/**
 * The category of item.
 * 
 * @author Ches Burks
 *
 */
public enum ItemType {
	/**
	 * Any sort of weapon or shield, held in the hands.
	 */
	WEAPON,
	/**
	 * Any sort of armor, worn on the body.
	 */
	ARMOR,
	/**
	 * Any sort of accessory that does not provide protection in the sense that
	 * armor does, worn on the body.
	 */
	ACCESSORY,
	/**
	 * An item that sockets into another one, to augment its stats.
	 */
	COMPONENT,
	/**
	 * An item that is consumed when used, like food or potions.
	 */
	CONSUMABLE,
	/**
	 * Material that is used for crafting or which has some use.
	 */
	MATERIAL,
	/**
	 * Has no use at all other than being sold for currency.
	 */
	JUNK,
	/**
	 * Items that are required for or related to quests.
	 */
	QUEST;
}