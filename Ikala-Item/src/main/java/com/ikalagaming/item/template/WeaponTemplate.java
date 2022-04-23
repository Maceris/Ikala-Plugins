package com.ikalagaming.item.template;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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
	 */
	private Integer maxDamage;
	private List<DamageModifierTemplate> extraDamage = new ArrayList<>();
	private List<DamageModifierTemplate> userResistanceBuffs =
		new ArrayList<>();
	private List<AttributeModifierTemplate> userBuffs = new ArrayList<>();
	private List<DamageModifierTemplate> targetResistanceDebuffs =
		new ArrayList<>();
	private List<AttributeModifierTemplate> targetDebuffs = new ArrayList<>();
}
