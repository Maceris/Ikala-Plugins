package com.ikalagaming.item;

/**
 * What kind of weapon an item is.
 * 
 * @author Ches Burks
 *
 */
public enum WeaponType {
	/**
	 * Melee weapons that are held in one hand, like swords, axes, or maces.
	 */
	ONE_HANDED_MELEE,
	/**
	 * Melee weapons that take two hands to hold, like great swords, great axes,
	 * mauls, or scythes.
	 */
	TWO_HANDED_MELEE,
	/**
	 * Ranged weapons that are held in one hand, like pistols.
	 */
	ONE_HANDED_RANGED,
	/**
	 * Ranged weapons that take two hands to hold, like rifles or crossbows.
	 */
	TWO_HANDED_RANGED,
	/**
	 * Magic weapons that are held in one hand, like scepters or wands.
	 */
	ONE_HANDED_MAGIC,
	/**
	 * Magic weapons that take two hands to hold, like staffs.
	 */
	TWO_HANDED_MAGIC,
	/**
	 * Items that are held in the off-hand that don't fit into the other
	 * one-handed categories, and generally provide buffs like tomes or orbs.
	 */
	OFF_HAND,
	/**
	 * Shields.
	 */
	SHIELD;
}
