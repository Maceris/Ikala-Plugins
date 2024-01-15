package com.ikalagaming.factory.quest;

/**
 * The types of rewards that can be given after completing a quest.
 *
 * @author Ches Burks
 *
 */
public enum RewardType {
	/**
	 * The player has a choice between a list of items.
	 */
	CHOICE,
	/**
	 * Runs a command or script.
	 */
	COMMAND,
	/**
	 * Give the player an item.
	 */
	ITEM,
	/**
	 * Unlock a recipe.
	 */
	RECIPE;
}
