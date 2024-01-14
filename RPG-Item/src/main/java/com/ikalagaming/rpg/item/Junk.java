package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.enums.ItemType;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Has no use at all other than being sold for currency.
 *
 * @author Ches Burks
 */
@Entity
@Table(name = Junk.TABLE_NAME)
public class Junk extends Item {
	/**
	 * The name of the table in the database.
	 */
	static final String TABLE_NAME = "JUNK";

	/**
	 * Construct a new junk item.
	 */
	public Junk() {
		this.setItemType(ItemType.JUNK);
	}
}
