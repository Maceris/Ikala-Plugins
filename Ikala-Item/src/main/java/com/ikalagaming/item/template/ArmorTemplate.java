package com.ikalagaming.item.template;

import com.ikalagaming.item.ArmorType;

import lombok.Getter;
import lombok.Setter;

/**
 * A template for generating armor from.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
public class ArmorTemplate extends EquipmentTemplate {

	/**
	 * Construct a new armor template.
	 */
	public ArmorTemplate() {}

	/**
	 * What kind of armor this is.
	 * 
	 * @param armorType The classification of armor.
	 * @return The classification of armor.
	 */
	private ArmorType armorType;
}
