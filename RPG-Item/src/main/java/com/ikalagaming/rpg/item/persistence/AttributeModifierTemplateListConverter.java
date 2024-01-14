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
public class AttributeModifierTemplateListConverter
	extends GenericConverter<List> {
	/**
	 * Generated serial version ID.
	 */
	private static final long serialVersionUID = 5710200045754311091L;

	@Override
	public Class<List> fromType() {
		return List.class;
	}
}
