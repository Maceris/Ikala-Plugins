package com.ikalagaming.item;

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
}
