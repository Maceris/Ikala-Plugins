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
	extends GenericConverter<ItemStatsTemplate> {}
