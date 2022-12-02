package com.ikalagaming.item;

import com.ikalagaming.item.enums.ItemType;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 *
 * Material that is used for crafting or which has some use.
 *
 * @author Ches Burks
 *
 */
@Entity
@Table(name = Consumable.TABLE_NAME)
public class Material extends Item {
	/**
	 * The name of the table in the database.
	 */
	static final String TABLE_NAME = "MATERIAL";

	/**
	 * Construct a new material item.
	 */
	public Material() {
		this.setItemType(ItemType.MATERIAL);
	}
}
