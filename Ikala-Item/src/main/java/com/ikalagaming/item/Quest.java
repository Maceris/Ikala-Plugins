package com.ikalagaming.item;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Items that are required for or related to quests.
 *
 * @author Ches Burks
 */
@Entity
@Table(name = Quest.TABLE_NAME)
public class Quest extends Item {
	/**
	 * The name of the table in the database.
	 */
	static final String TABLE_NAME = "QUEST_ITEM";
}
