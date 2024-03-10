package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.enums.ConsumableType;
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
 * An item that is consumed by using it.
 *
 * @author Ches Burks
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = Consumable.TABLE_NAME)
public class Consumable extends Item {
    /** The name of the table in the database. */
    static final String TABLE_NAME = "CONSUMABLE";

    /**
     * What kind of consumable this item is.
     *
     * @param consumableType The type of consumable this item is.
     * @return The type of consumable this item is.
     */
    @Column(name = "CONSUMABLE_TYPE")
    @Enumerated(EnumType.STRING)
    private ConsumableType consumableType;

    /** Constructs a new consumable item. */
    public Consumable() {
        setItemType(ItemType.CONSUMABLE);
    }
}
