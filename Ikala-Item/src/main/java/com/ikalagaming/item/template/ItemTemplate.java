package com.ikalagaming.item.template;

import com.ikalagaming.item.ItemType;
import com.ikalagaming.item.Quality;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An item in the game.
 *
 * @author Ches Burks
 *
 */
@NoArgsConstructor
@Getter
@Setter
public class ItemTemplate {
	/**
	 * The unique ID of this type of item.
	 *
	 * @param id The unique ID for this item type.
	 * @return The unique ID for this item type.
	 */
	private Integer id;
	/**
	 * The category of the item, such as a weapon or consumable.
	 *
	 * @param type The type of the item, like a weapon or consumable.
	 * @return The type of the item, like a weapon or consumable.
	 */
	private ItemType type;
	/**
	 * The (unlocalized) name of the item.
	 *
	 * @param name The name of the item.
	 * @return The name of the item.
	 */
	private String name;
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
}
