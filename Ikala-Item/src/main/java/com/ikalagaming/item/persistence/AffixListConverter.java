package com.ikalagaming.item.persistence;

import java.util.List;

import javax.persistence.Converter;

/**
 * Convert lists of attribute modifiers.
 *
 * @author Ches Burks
 *
 */
@SuppressWarnings("rawtypes")
@Converter
public class AffixListConverter extends GenericConverter<List> {

	/**
	 * Generated serial version ID.
	 */
	private static final long serialVersionUID = -1612614385107662896L;

	@Override
	public Class<List> fromType() {
		return List.class;
	}
}
