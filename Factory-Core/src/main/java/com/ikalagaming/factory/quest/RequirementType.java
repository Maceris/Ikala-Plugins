package com.ikalagaming.factory.quest;

/**
 * The types of requirement that must be satisfied for a quest to be considered
 * completed.
 *
 * @author Ches Burks
 *
 */
public enum RequirementType {
	/**
	 * Break a specific type of block.
	 */
	BREAK_BLOCK,
	/**
	 * The player just has to click on a checkbox on the quest.
	 */
	CHECKBOX,
	/**
	 * The player has to craft a specific thing.
	 */
	CRAFT,
	/**
	 * The player has to interact with a specific kind of entity.
	 */
	INTERACT_ENTITY,
	/**
	 * The player has to reach a specific location (position, structure, biome,
	 * dimension).
	 */
	LOCATION,
	/**
	 * The player has to have a certain item in their inventory.
	 */
	RETRIEVE,
	/**
	 * The player has to use a specific kind of item.
	 */
	USE_ITEM;
}
