package com.ikalagaming.item;

import com.ikalagaming.item.persistence.AffixListConverter;
import com.ikalagaming.item.persistence.AttributeModifierListConverter;
import com.ikalagaming.item.persistence.ItemStatsConverter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.MappedSuperclass;

/**
 * An item that has some stats and requirements.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@MappedSuperclass
public class Equipment extends Item {
	/**
	 * The level requirement required for using this equipment.
	 *
	 * @param levelRequirement The level that is required in order to equip this
	 *            item.
	 * @return The level that is required in order to equip this item.
	 */
	@Column(name = "LEVEL_REQUIREMENT")
	private Integer levelRequirement;

	/**
	 * The minimum attribute values that are required for using this equipment.
	 *
	 * @param attributeRequirements The minimum attribute values that are
	 *            required for using this equipment.
	 * @return The minimum attribute values that are required for using this
	 *         equipment.
	 */
	@Column(name = "ATTRIBUTE_REQUIREMENTS")
	@Convert(converter = AttributeModifierListConverter.class)
	private List<AttributeModifier> attributeRequirements = new ArrayList<>();

	/**
	 * Stat bonuses provided by the item.
	 *
	 * @param itemStats The stat bonuses provided by the item.
	 * @return The stat bonuses provided by the item.
	 */
	@Column(name = "ITEM_STATS")
	@Convert(converter = ItemStatsConverter.class)
	private ItemStats itemStats = new ItemStats();

	/**
	 * The affixes for this equipment.
	 *
	 * @param affixes The affixes for this item.
	 * @return The affixes for this item.
	 */
	@Column(name = "AFFIXES")
	@Convert(converter = AffixListConverter.class)
	private List<Affix> affixes = new ArrayList<>();

	/**
	 * Construct a new equipment item.
	 */
	public Equipment() {}

}
