package com.ikalagaming.item;

import com.ikalagaming.item.enums.ItemType;
import com.ikalagaming.item.enums.Quality;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * An item in the game.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode
@Getter
@Setter
public class Item {
	/**
	 * The category of the item, such as a weapon or consumable.
	 *
	 * @param itemType The type of the item, like a weapon or consumable.
	 * @return The type of the item, like a weapon or consumable.
	 */
	private ItemType itemType;

	/**
	 * The unique name of the item, which should follow a standard format and be
	 * human readable but unlocalized.
	 *
	 * @param ID The name of the item in the database/code level.
	 * @return The name of the item in the database/code level.
	 */
	private String ID;
	/**
	 * The quality of the items, which helps specify how rare or powerful it is.
	 * This is associated with UI indicators to quickly communicate to the
	 * player how good an item is at a glance, and used for filtering.
	 *
	 * @param quality The quality the item is.
	 * @return The quality of the item.
	 */
	private Quality quality;
	/**
	 * The expected level that this item should drop in. Level 10 items would be
	 * dropped by level 10 enemies or in areas they might be found. This may be
	 * scaled but helps indicate relative quality of items in addition to their
	 * literal quality.
	 *
	 * @param itemLevel The expected level where this item is expected to be
	 *            encountered.
	 * @return The expected level where this item is expected to be encountered.
	 */
	private Integer itemLevel;

	/**
	 * Construct a new item.
	 */
	public Item() {}
}
