package com.ikalagaming.item.template;

import com.ikalagaming.item.AttributeModifier;
import com.ikalagaming.item.Item;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A template for equipment.
 * 
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class EquipmentTemplate extends Item {
	/**
	 * Construct a new equipment template.
	 */
	public EquipmentTemplate() {}

	/**
	 * The level requirement required for using this equipment.
	 * 
	 * @param levelRequirement The level that is required in order to equip this
	 *            item.
	 * @return The level that is required in order to equip this item.
	 */
	private Integer levelRequirement;
	/**
	 * The minimum attribute values that are required for using this equipment.
	 * 
	 * @param attributeRequirements The minimum attribute values that are
	 *            required for using this equipment.
	 * @return The minimum attribute values that are required for using this
	 *         equipment.
	 */
	private List<AttributeModifier> attributeRequirements = new ArrayList<>();
	/**
	 * Stat bonuses provided by the item.
	 * 
	 * @param itemStats The stat bonuses provided by the item.
	 * @return The stat bonuses provided by the item.
	 */
	private ItemStatsTemplate itemStats = new ItemStatsTemplate();
}
