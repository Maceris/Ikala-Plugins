package com.ikalagaming.item.template;

import com.ikalagaming.item.enums.ArmorType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

/**
 * A template for generating armor from.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = ArmorTemplate.TABLE_NAME)
public class ArmorTemplate extends EquipmentTemplate {
	/**
	 * The name of the table in the database.
	 */
	static final String TABLE_NAME = "ARMOR_TEMPLATE";

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
	 * Construct a new armor template.
	 */
	public ArmorTemplate() {}
}
