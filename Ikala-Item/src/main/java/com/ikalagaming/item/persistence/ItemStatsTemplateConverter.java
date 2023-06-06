package com.ikalagaming.item.persistence;

import com.ikalagaming.item.template.ItemStatsTemplate;

import javax.persistence.Converter;

/**
 * Convert item stats.
 *
 * @author Ches Burks
 *
 */
@Converter
public class ItemStatsTemplateConverter
	extends GenericConverter<ItemStatsTemplate> {

	/**
	 * Generated serial version ID.
	 */
	private static final long serialVersionUID = -4688117393051943140L;

	@Override
	public Class<ItemStatsTemplate> fromType() {
		return ItemStatsTemplate.class;
	}
}
