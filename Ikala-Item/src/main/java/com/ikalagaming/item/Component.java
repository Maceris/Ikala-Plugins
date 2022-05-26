package com.ikalagaming.item;

import com.ikalagaming.item.enums.ComponentType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * An item that slots into and empowers or modifies another item.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class Component extends Item {

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
	private ItemStats itemStats = new ItemStats();

	/**
	 * The types of items this component can be applied to.
	 *
	 * @param itemCriteria The types of items this component can be applied to.
	 * @return The types of items this component can be applied to.
	 */
	private ItemCriteria itemCriteria = new ItemCriteria();

	/**
	 * Constructs a new component.
	 */
	public Component() {}
}
