package com.ikalagaming.rpg.item.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What type of component an item is.
 *
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public enum ComponentType {
	/**
	 * Provides some kind of augmentation to an item.
	 */
	AUGMENT("augment_"),
	/**
	 * A gem that slots into or is attached to an item.
	 */
	GEM("gem_");

	/**
	 * The prefix that is used in the IDs for components of each type in the
	 * database, after the item type prefix.
	 *
	 * @return The prefix that appears before components of each type, after the
	 *         item type prefix, in the database.
	 */
	private final String prefix;
}
