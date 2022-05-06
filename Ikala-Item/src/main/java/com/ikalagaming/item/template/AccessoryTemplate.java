package com.ikalagaming.item.template;

import com.ikalagaming.item.AccessoryType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * A template for generating accessories from.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class AccessoryTemplate extends EquipmentTemplate {

	/**
	 * Construct a new accessory template.
	 */
	public AccessoryTemplate() {}

	/**
	 * What kind of accessory this is.
	 * 
	 * @param accessoryType The classification of accessory.
	 * @return The classification of accessory.
	 */
	private AccessoryType accessoryType;
}
