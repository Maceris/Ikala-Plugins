package com.ikalagaming.item.template;

import com.ikalagaming.item.DamageModifier;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks modification of damage, or damage resistance.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class DamageModifierTemplate extends DamageModifier {
	/**
	 * Construct a new damage modifier template.
	 */
	public DamageModifierTemplate() {}

	/**
	 * How far away from the base value we can go, as an integer percentage. For
	 * example, a value of 10 would mean we can generate values +/- 10% from the
	 * base.
	 *
	 * @param variance The percentage variance.
	 * @return The percentage variance in the base amount.
	 */
	private Integer variance;

}
