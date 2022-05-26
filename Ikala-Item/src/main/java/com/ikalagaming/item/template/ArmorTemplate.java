package com.ikalagaming.item.template;

import com.ikalagaming.item.enums.ArmorType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * A template for generating armor from.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class ArmorTemplate extends EquipmentTemplate {

	/**
	 * What kind of armor this is.
	 *
	 * @param armorType The classification of armor.
	 * @return The classification of armor.
	 */
	private ArmorType armorType;

	/**
	 * Construct a new armor template.
	 */
	public ArmorTemplate() {}
}
