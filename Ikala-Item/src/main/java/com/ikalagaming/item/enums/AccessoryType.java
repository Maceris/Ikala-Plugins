package com.ikalagaming.item.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What kind of accessory an item is.
 *
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public enum AccessoryType {
	/**
	 * Worn around the neck, like an amulet or necklace.
	 */
	AMULET("amulet_"),
	/**
	 * Worn on the waist, like a belt.
	 */
	BELT("belt_"),
	/**
	 * Worn on the back, like a cape.
	 */
	CAPE("cape_"),
	/**
	 * A ring.
	 */
	RING("ring_"),
	/**
	 * A minor trinket, such as an earring, medal, broach, bauble, or talisman.
	 */
	TRINKET("trinket_");

	/**
	 * The prefix that is used in the IDs for accessories of each type in the
	 * database, after the item type prefix.
	 *
	 * @return The prefix that appears before accessories of each type, after
	 *         the item type prefix, in the database.
	 */
	private final String prefix;
}
