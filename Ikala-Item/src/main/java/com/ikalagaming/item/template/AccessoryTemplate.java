package com.ikalagaming.item.template;

import com.ikalagaming.item.AccessoryType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A template for generating accessories from.
 *
 * @author Ches Burks
 *
 */
@NoArgsConstructor
@Getter
@Setter
public class AccessoryTemplate extends EquipmentTemplate {
	/**
	 * What kind of accessory this is.
	 * 
	 * @param accessoryType The classification of accessory.
	 * @return The classification of accessory.
	 */
	private AccessoryType accessoryType;
}
