package com.ikalagaming.item.template;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A template for generating weapons from.
 *
 * @author Ches Burks
 *
 */
@NoArgsConstructor
@Getter
@Setter
public class WeaponTemplate extends ItemTemplate {
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
	 * Extra damage that this weapon does by default.
	 *
	 * @param extraDamage Extra damage done by the weapon on top of its standard
	 *            attack damage.
	 * @return Extra damage done by the weapon on top of its standard attack
	 *         damage.
	 */
	private List<DamageModifierTemplate> extraDamage = new ArrayList<>();
	/**
	 * Buffs to damage resistance that the weapon provides to the wielder.
	 *
	 * @param userResistanceBuffs Damage resistance that the weapon provides to
	 *            the wielder.
	 * @return Damage resistance that the weapon provides to the wielder.
	 */
	private List<DamageModifierTemplate> userResistanceBuffs =
		new ArrayList<>();
	/**
	 * Buffs to attributes that the weapon provides to the wielder.
	 *
	 * @param userBuffs Buffs to attributes that the weapon provides to the
	 *            wielder.
	 * @return Buffs to attributes that the weapon provides to the wielder.
	 */
	private List<AttributeModifierTemplate> userBuffs = new ArrayList<>();
}
