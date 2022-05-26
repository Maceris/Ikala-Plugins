package com.ikalagaming.item.template;

import com.ikalagaming.item.enums.WeaponType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * A template for generating weapons from.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class WeaponTemplate extends EquipmentTemplate {
	/**
	 * The minimum damage that this weapon does, usually damage done per hit is
	 * randomly distributed within a range of values.
	 *
	 * @param minDamage The lower bound of default damage for this type of
	 *            weapon.
	 * @return The lower bound of default damage for this type of weapon.
	 */
	private Integer minDamage;

	/**
	 * The maximum damage that this weapon does, usually damage done per hit is
	 * randomly distributed within a range of values.
	 *
	 * @param maxDamage The upper bound of default damage for this type of
	 *            weapon.
	 * @return The upper bound of default damage for this type of weapon.
	 */
	private Integer maxDamage;
	/**
	 * What kind of weapon this is.
	 *
	 * @param weaponType The classification of weapon.
	 * @return The classification of weapon.
	 */
	private WeaponType weaponType;

	/**
	 * Construct a new weapon template.
	 */
	public WeaponTemplate() {}

}
