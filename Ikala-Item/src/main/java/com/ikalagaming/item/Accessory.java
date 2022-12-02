package com.ikalagaming.item;

import com.ikalagaming.item.enums.AccessoryType;
import com.ikalagaming.item.enums.ItemType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

/**
 * An accessory like rings that are worn to increase power.
 *
 * @author Ches Burks
 *
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = Accessory.TABLE_NAME)
public class Accessory extends Equipment {
	/**
	 * The name of the table in the database.
	 */
	static final String TABLE_NAME = "ACCESSORY";
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
	 * Construct a new accessory.
	 */
	public Accessory() {
		this.setItemType(ItemType.ACCESSORY);
	}
}
