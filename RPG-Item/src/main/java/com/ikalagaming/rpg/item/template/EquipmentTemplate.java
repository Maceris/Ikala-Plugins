package com.ikalagaming.rpg.item.template;

import com.ikalagaming.rpg.item.Item;
import com.ikalagaming.rpg.item.persistence.AttributeModifierTemplateListConverter;
import com.ikalagaming.rpg.item.persistence.ItemStatsTemplateConverter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Convert;

/**
 * A template for equipment.
 *
 * @author Ches Burks
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class EquipmentTemplate extends Item {
    /**
     * The level requirement required for using this equipment.
     *
     * @param levelRequirement The level that is required in order to equip this item.
     * @return The level that is required in order to equip this item.
     */
    @Column(name = "LEVEL_REQUIREMENT")
    private Integer levelRequirement;

    /**
     * The minimum attribute values that are required for using this equipment.
     *
     * @param attributeRequirements The minimum attribute values that are required for using this
     *     equipment.
     * @return The minimum attribute values that are required for using this equipment.
     */
    @Column(name = "ATTRIBUTE_REQUIREMENTS")
    @Convert(converter = AttributeModifierTemplateListConverter.class)
    private List<AttributeModifierTemplate> attributeRequirements = new ArrayList<>();

    /**
     * Stat bonuses provided by the item.
     *
     * @param itemStatsTemplate The stat bonuses provided by the item.
     * @return The stat bonuses provided by the item.
     */
    @Column(name = "ITEM_STATS_TEMPLATE")
    @Convert(converter = ItemStatsTemplateConverter.class)
    private ItemStatsTemplate itemStatsTemplate = new ItemStatsTemplate();

    /** Construct a new equipment template. */
    public EquipmentTemplate() {}
}
