package com.ikalagaming.item;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An item that has some stats and requirements.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class Equipment extends Item {
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
	private ItemStats itemStats = new ItemStats();

	/**
	 * Construct a new equipment item.
	 */
	public Equipment() {}

}
