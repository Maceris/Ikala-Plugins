package com.ikalagaming.item;

import com.ikalagaming.item.enums.AccessoryType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * An accessory like rings that are worn to increase power.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class Accessory extends Equipment {
	/**
	 * What kind of accessory this is.
	 *
	 * @param accessoryType The classification of accessory.
	 * @return The classification of accessory.
	 */
	private AccessoryType accessoryType;

	/**
	 * Construct a new accessory.
	 */
	public Accessory() {}
}
