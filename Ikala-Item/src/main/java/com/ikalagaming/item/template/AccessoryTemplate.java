package com.ikalagaming.item.template;

import com.ikalagaming.item.enums.AccessoryType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * A template for generating accessories from.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class AccessoryTemplate extends EquipmentTemplate {

	/**
	 * What kind of accessory this is.
	 *
	 * @param accessoryType The classification of accessory.
	 * @return The classification of accessory.
	 */
	@Column(name = "ACCESSORY_TYPE")
	@Enumerated(EnumType.STRING)
	private AccessoryType accessoryType;

	/**
	 * Construct a new accessory template.
	 */
	public AccessoryTemplate() {}
}
