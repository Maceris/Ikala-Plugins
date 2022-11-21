package com.ikalagaming.item;

import com.ikalagaming.item.enums.ComponentType;
import com.ikalagaming.item.persistence.ItemCriteriaConverter;
import com.ikalagaming.item.persistence.ItemStatsConverter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

/**
 * An item that slots into and empowers or modifies another item.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = Component.TABLE_NAME)
public class Component extends Item {
	/**
	 * The name of the table in the database.
	 */
	static final String TABLE_NAME = "COMPONENT";
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
	 * Stat bonuses provided by the item.
	 *
	 * @param itemStats The stat bonuses provided by the item.
	 * @return The stat bonuses provided by the item.
	 */
	@Column(name = "ITEM_STATS")
	@Convert(converter = ItemStatsConverter.class)
	private ItemStats itemStats = new ItemStats();

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
	 * Constructs a new component.
	 */
	public Component() {}
}
