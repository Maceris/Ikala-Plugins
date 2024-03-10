package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.enums.ItemType;
import com.ikalagaming.rpg.item.enums.Quality;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

/**
 * An item in the game.
 *
 * @author Ches Burks
 */
@EqualsAndHashCode
@Getter
@Setter
@MappedSuperclass
public abstract class Item {
    /**
     * The category of the item, such as a weapon or consumable.
     *
     * @param itemType The type of the item, like a weapon or consumable.
     * @return The type of the item, like a weapon or consumable.
     */
    @Column(name = "ITEM_TYPE")
    @Enumerated(EnumType.STRING)
    private ItemType itemType;

    /**
     * The unique name of the item, which should follow a standard format and be human readable but
     * unlocalized.
     *
     * @param ID The name of the item in the database/code level.
     * @return The name of the item in the database/code level.
     */
    @Id private String ID;

    /**
     * The quality of the items, which helps specify how rare or powerful it is. This is associated
     * with UI indicators to quickly communicate to the player how good an item is at a glance, and
     * used for filtering.
     *
     * @param quality The quality the item is.
     * @return The quality of the item.
     */
    @Column(name = "QUALITY")
    @Enumerated(EnumType.STRING)
    private Quality quality;

    /**
     * The expected level that this item should drop in. Level 10 items would be dropped by level 10
     * enemies or in areas they might be found. This may be scaled but helps indicate relative
     * quality of items in addition to their literal quality.
     *
     * @param itemLevel The expected level where this item is expected to be encountered.
     * @return The expected level where this item is expected to be encountered.
     */
    @Column(name = "ITEM_LEVEL")
    private Integer itemLevel;

    /** Construct a new item. */
    public Item() {}
}
