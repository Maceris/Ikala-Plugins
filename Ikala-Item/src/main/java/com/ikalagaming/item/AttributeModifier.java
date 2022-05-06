package com.ikalagaming.item;

import com.ikalagaming.attributes.Attribute;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks modification of an attribute.
 *
 * @author Ches Burks
 *
 */
@NoArgsConstructor
@Getter
@Setter
public class AttributeModifier {
	/**
	 * The specific attribute this modifier affects.
	 *
	 * @param attribute The attribute this modifies.
	 * @return The attribute this modifies.
	 */
	private Attribute attribute;
	/**
	 * How this modifier affects the base value, such as a flat change,
	 * percentage difference, or a range of possible values.
	 *
	 * @param type The way that this affects the base value of the stat/concept
	 *            it modifies.
	 * @return The way that this affects the base value of the stat/concept it
	 *         modifies.
	 */
	private ModifierType type;
	/**
	 * The amount the attribute is modified by, where the type decides if this
	 * is used as a flat number or percentage.
	 *
	 * @param amount The new amount.
	 * @return The amount the attribute is modified by.
	 */
	private Integer amount;

}
