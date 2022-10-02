package com.ikalagaming.item.template;

import com.ikalagaming.item.Item;
import com.ikalagaming.item.ItemCriteria;
import com.ikalagaming.item.enums.ComponentType;
import com.ikalagaming.item.persistence.ItemCriteriaConverter;
import com.ikalagaming.item.persistence.ItemStatsTemplateConverter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 *
 * Slots into an item to give it extra bonus stats.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class ComponentTemplate extends Item {
	/**
	 * What kind of component the item is.
	 *
	 * @param componentType The kind of component the item is.
	 * @return The kind of component the item is.
	 */
	@Column(name = "COMPONENT_TYPE")
	@Enumerated(EnumType.STRING)
	private ComponentType componentType;

	/**
	 * The minimum item level requirement for this item.
	 *
	 * @param levelRequirement The item level requirement for this item.
	 * @return The item level requirement for this item.
	 */
	@Column(name = "LEVEL_REQUIREMENT")
	private Integer levelRequirement;

	/**
	 * Stat bonuses provided by the item.
	 *
	 * @param itemStats The stat bonuses provided by the item.
	 * @return The stat bonuses provided by the item.
	 */
	@Column(name = "ITEM_STATS")
	@Convert(converter = ItemStatsTemplateConverter.class)
	private ItemStatsTemplate itemStats = new ItemStatsTemplate();

	/**
	 * The types of items this component can be applied to.
	 *
	 * @param itemCriteria The types of items this component can be applied to.
	 * @return The types of items this component can be applied to.
	 */
	@Column(name = "ITEM_CRITERIA")
	@Convert(converter = ItemCriteriaConverter.class)
	private ItemCriteria itemCriteria = new ItemCriteria();

	/**
	 * Construct a new component template.
	 */
	public ComponentTemplate() {}
}
