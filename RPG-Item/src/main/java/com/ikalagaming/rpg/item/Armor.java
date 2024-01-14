package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.enums.ArmorType;
import com.ikalagaming.rpg.item.enums.ItemType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

/**
 * Armor that is worn for protection or power.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Entity
@Table(name = Armor.TABLE_NAME)
public class Armor extends Equipment {
	/**
	 * The name of the table in the database.
	 */
	static final String TABLE_NAME = "ARMOR";
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
	public Armor() {
		this.setItemType(ItemType.ARMOR);
	}
}
