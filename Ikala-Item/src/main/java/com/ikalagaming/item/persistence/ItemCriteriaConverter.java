package com.ikalagaming.item.persistence;

import com.ikalagaming.item.ItemCriteria;

import javax.persistence.Converter;

/**
 * Convert item criteria.
 *
 * @author Ches Burks
 *
 */
@Converter
public class ItemCriteriaConverter extends GenericConverter<ItemCriteria> {
	/**
	 * Generated serial version ID.
	 */
	private static final long serialVersionUID = -5028800939691407371L;

	@Override
	public Class<ItemCriteria> fromType() {
		return ItemCriteria.class;
	}
}
