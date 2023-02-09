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
	 * How far away from the base value we can go. For example, a base value of
	 * 100 and a variance of 10 would mean we can generate values between 90 and
	 * 110. Should be between 0 and the amount so that values generated are
	 * positive and reasonable.
	 *
	 * @param variance The percentage variance.
	 * @return The percentage variance in the base amount.
	 */
	private Integer variance;

	/**
	 * Construct a new damage modifier template.
	 */
	public DamageModifierTemplate() {}

}
