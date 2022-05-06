package com.ikalagaming.item.template;

import com.ikalagaming.item.ArmorType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A template for generating armor from.
 *
 * @author Ches Burks
 *
 */
@NoArgsConstructor
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
}
