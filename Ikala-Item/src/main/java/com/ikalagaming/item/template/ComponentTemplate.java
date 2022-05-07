package com.ikalagaming.item.template;

import com.ikalagaming.item.Item;
import com.ikalagaming.item.enums.ComponentType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * Slots into an item to give it extra bonus stats.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class ComponentTemplate extends Item {
	/**
	 * Construct a new component template.
	 */
	public ComponentTemplate() {}

	/**
	 * What kind of component the item is.
	 * 
	 * @param componentType The kind of component the item is.
	 * @return The kind of component the item is.
	 */
	private ComponentType componentType;
	/**
	 * Stat bonuses provided by the item.
	 * 
	 * @param itemStats The stat bonuses provided by the item.
	 * @return The stat bonuses provided by the item.
	 */
	private ItemStatsTemplate itemStats = new ItemStatsTemplate();
}
