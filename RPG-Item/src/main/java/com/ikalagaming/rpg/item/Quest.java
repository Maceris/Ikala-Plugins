package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.enums.ItemType;

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
    /** The name of the table in the database. */
    static final String TABLE_NAME = "QUEST_ITEM";

    /** Construct a new quest item. */
    public Quest() {
        setItemType(ItemType.QUEST);
    }
}
