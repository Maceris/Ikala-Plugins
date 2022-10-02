package com.ikalagaming.item;

import com.ikalagaming.item.enums.ArmorType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * Armor that is worn for protection or power.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
public class Armor extends Equipment {

	/**
	 * What kind of armor this is.
	 *
	 * @param armorType The classification of armor.
	 * @return The classification of armor.
	 */
	@Column(name = "ARMOR_TYPE")
	@Enumerated(EnumType.STRING)
	private ArmorType armorType;

	/**
	 * Constructs a new armor item.
	 */
	public Armor() {}
}
