package com.ikalagaming.rpg.item.persistence;

import com.ikalagaming.rpg.item.ItemStats;

import javax.persistence.Converter;

/**
 * Convert item stats.
 *
 * @author Ches Burks
 */
@Converter
public class ItemStatsConverter extends GenericConverter<ItemStats> {
    /** Generated serial version ID. */
    private static final long serialVersionUID = 1072820932867807214L;

    @Override
    public Class<ItemStats> fromType() {
        return ItemStats.class;
    }
}
