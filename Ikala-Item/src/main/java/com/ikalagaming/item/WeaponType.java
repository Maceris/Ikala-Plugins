package com.ikalagaming.item;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What kind of weapon an item is.
 *
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public enum WeaponType {
	/**
	 * Melee weapons that are held in one hand, like swords, axes, or maces.
	 */
	ONE_HANDED_MELEE("one_handed_melee_"),
	/**
	 * Melee weapons that take two hands to hold, like great swords, great axes,
	 * mauls, or scythes.
	 */
	TWO_HANDED_MELEE("two_handed_melee_"),
	/**
	 * Ranged weapons that are held in one hand, like pistols.
	 */
	ONE_HANDED_RANGED("one_handed_ranged_"),
	/**
	 * Ranged weapons that take two hands to hold, like rifles or crossbows.
	 */
	TWO_HANDED_RANGED("two_handed_ranged_"),
	/**
	 * Magic weapons that are held in one hand, like scepters or wands.
	 */
	ONE_HANDED_MAGIC("one_handed_magic_"),
	/**
	 * Magic weapons that take two hands to hold, like staffs.
	 */
	TWO_HANDED_MAGIC("two_handed_magic_"),
	/**
	 * Items that are held in the off-hand that don't fit into the other
	 * one-handed categories, and generally provide buffs like tomes or orbs.
	 */
	OFF_HAND("off_hand_"),
	/**
	 * Shields.
	 */
	SHIELD("shield_");

	/**
	 * The prefix that is used in the IDs for weapons of each type in the
	 * database, after the item type prefix.
	 *
	 * @return The prefix that appears before weapons of each type, after the
	 *         item type prefix, in the database.
	 */
	private final String prefix;
}
