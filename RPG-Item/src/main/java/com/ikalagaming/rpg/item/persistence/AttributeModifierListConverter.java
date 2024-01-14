package com.ikalagaming.rpg.item.persistence;

import java.util.List;

import javax.persistence.Converter;

/**
 * Convert lists of affixes.
 *
 * @author Ches Burks
 *
 */
@SuppressWarnings("rawtypes")
@Converter
public class AttributeModifierListConverter extends GenericConverter<List> {
	/**
	 * Generated serial version ID.
	 */
	private static final long serialVersionUID = -6593077003591940917L;

	@Override
	public Class<List> fromType() {
		return List.class;
	}
}
