package com.ikalagaming.item;

/**
 * The quality of items.
 * 
 * @author Ches Burks
 *
 */
public enum Quality {
	/**
	 * Items that no longer, or never did, have any use except for selling.
	 */
	TRASH,
	/**
	 * Easily found, frequently dropped by monsters or found in containers.
	 * Rarely may have some magical properties.
	 */
	COMMON,
	/**
	 * Uncommon items that have magical properties. Usually have one or two
	 * magical affixes that add extra attributes.
	 */
	MAGIC,
	/**
	 * Rare magical items that have affixes like magical items but have more
	 * powerful stats and affixes available.
	 */
	RARE,
	/**
	 * Very powerful items, with unique stats and properties.
	 */
	EPIC,
	/**
	 * A tier of unique items that are more rare and powerful than epic items.
	 */
	LEGENDARY;
}
