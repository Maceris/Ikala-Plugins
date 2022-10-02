package com.ikalagaming.item;

import com.ikalagaming.item.enums.WeaponType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * A specific weapon with stats.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class Weapon extends Equipment {

	/**
	 * The minimum damage that this weapon does, usually damage done per hit is
	 * randomly distributed within a range of values.
	 *
	 * @param minDamage The lower bound of default damage for this type of
	 *            weapon.
	 * @return The lower bound of default damage for this type of weapon.
	 */
	@Column(name = "MIN_DAMAGE")
	private Integer minDamage;

	/**
	 * The maximum damage that this weapon does, usually damage done per hit is
	 * randomly distributed within a range of values.
	 *
	 * @param maxDamage The upper bound of default damage for this type of
	 *            weapon.
	 * @return The upper bound of default damage for this type of weapon.
	 */
	@Column(name = "MAX_DAMAGE")
	private Integer maxDamage;
	/**
	 * What kind of weapon this is.
	 *
	 * @param weaponType The classification of weapon.
	 * @return The classification of weapon.
	 */
	@Column(name = "WEAPON_TYPE")
	@Enumerated(EnumType.STRING)
	private WeaponType weaponType;

	/**
	 * Constructs a new weapon object.
	 */
	public Weapon() {}
}
