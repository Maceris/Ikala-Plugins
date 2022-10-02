package com.ikalagaming.item;

import com.ikalagaming.attributes.Attribute;
import com.ikalagaming.item.enums.ModifierType;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * Tracks modification of an attribute.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
public class AttributeModifier {
	/**
	 * The specific attribute this modifier affects.
	 *
	 * @param attribute The attribute this modifies.
	 * @return The attribute this modifies.
	 */
	@Column(name = "ATTRIBUTE")
	@Enumerated(EnumType.STRING)
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
	@Column(name = "TYPE")
	@Enumerated(EnumType.STRING)
	private ModifierType type;
	/**
	 * The amount the attribute is modified by, where the type decides if this
	 * is used as a flat number or percentage.
	 *
	 * @param amount The new amount.
	 * @return The amount the attribute is modified by.
	 */
	@Column(name = "AMOUNT")
	private Integer amount;

	/**
	 * Construct a new attribute modifier.
	 */
	public AttributeModifier() {}

}
