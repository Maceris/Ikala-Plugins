package com.ikalagaming.item;

import com.ikalagaming.item.enums.ConsumableType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * An item that is consumed by using it.
 * 
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class Consumable extends Item {
	/**
	 * What kind of consumable this item is.
	 *
	 * @param consumableType The type of consumable this item is.
	 * @return The type of consumable this item is.
	 */
	private ConsumableType consumableType;

	/**
	 * Constructs a new consumable item.
	 */
	public Consumable() {}
}
