package com.ikalagaming.item.persistence;

import com.ikalagaming.item.ItemStats;

import javax.persistence.Converter;

/**
 * Convert item stats.
 * 
 * @author Ches Burks
 *
 */
@Converter
public class ItemStatsConverter extends GenericConverter<ItemStats> {}
