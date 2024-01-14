package com.ikalagaming.rpg.item.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What type of consumable an item is.
 *
 * @author Ches Burks
 *
 */
@Getter
@AllArgsConstructor
public enum ConsumableType {
	/**
	 * Provides healing over time.
	 */
	BANDAGE("bandage_"),
	/**
	 * Consumed typically to restore mana over time, but could restore other
	 * stats or give buffs.
	 */
	DRINK("drink_"),
	/**
	 * Drinkable items that provide an effect to the drinker such as buffs,
	 * similar to potions, but typically last longer.
	 */
	ELIXIR("elixir_"),
	/**
	 * Consumed typically to restore health over time, but could restore other
	 * stats or give buffs.
	 */
	FOOD("food_"),
	/**
	 * Drinkable items that provide an effect to the drinker, such as healing,
	 * or buffs.
	 */
	POTION("potion_"),
	/**
	 * Must be read to be used, may provide a buff effect, perform a magical
	 * spell when used, or contain a message.
	 */
	SCROLL("scroll_");

	/**
	 * The prefix that is used in the IDs for consumables of each type in the
	 * database, after the item type prefix.
	 *
	 * @return The prefix that appears before consumables of each type, after
	 *         the item type prefix, in the database.
	 */
	private final String prefix;
}
