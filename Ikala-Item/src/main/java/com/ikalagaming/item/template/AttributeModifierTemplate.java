package com.ikalagaming.item.template;

import com.ikalagaming.item.AttributeModifier;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks modification of an attribute.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class AttributeModifierTemplate extends AttributeModifier {
	/**
	 * Construct a new attribute modifier template.
	 */
	public AttributeModifierTemplate() {}

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
