package com.ikalagaming.item;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What type of armor an item is.
 *
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public enum ArmorType {
	/**
	 * Worn on the head, like helmets or hats.
	 */
	HEAD("head_"),
	/**
	 * Worn on the shoulders.
	 */
	SHOULDERS("shoulders_"),
	/**
	 * Worn on the chest, like chest plates and shirts.
	 */
	CHEST("chest_"),
	/**
	 * Worn on the wrists or arms, like bracers.
	 */
	WRIST("wrist_"),
	/**
	 * Worn on the hands, like gloves.
	 */
	HANDS("hands_"),
	/**
	 * Worn on the legs, like trousers.
	 */
	LEGS("legs_"),
	/**
	 * Worn on the feet, like shoes.
	 */
	FEET("feet_");

	/**
	 * The prefix that is used in the IDs for armor of each type in the
	 * database, after the item type prefix.
	 *
	 * @return The prefix that appears before armor of each type, after the item
	 *         type prefix, in the database.
	 */
	private final String prefix;
}
