package com.ikalagaming.item.template;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Slots into an item to give it extra bonus stats.
 *
 * @author Ches Burks
 *
 */
@NoArgsConstructor
@Getter
@Setter
public class ComponentTemplate extends ItemTemplate {
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
