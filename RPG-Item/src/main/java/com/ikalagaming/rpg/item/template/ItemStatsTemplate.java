package com.ikalagaming.rpg.item.template;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A block of item stats.
 *
 * @author Ches Burks
 */
@EqualsAndHashCode
@Getter
@Setter
public class ItemStatsTemplate {
    /**
     * Buffs to damage done by the wielder of this item.
     *
     * @param damageBuffs Buffs to damage done by the wielder of this item.
     * @return Buffs to damage done by the wielder of this item.
     */
    private List<DamageModifierTemplate> damageBuffs = new ArrayList<>();

    /**
     * Buffs to damage resistance that the item provides to the wielder.
     *
     * @param resistanceBuffs Damage resistance that the item provides to the wielder.
     * @return Damage resistance that the item provides to the wielder.
     */
    private List<DamageModifierTemplate> resistanceBuffs = new ArrayList<>();

    /**
     * Buffs to attributes that the item provides to the wielder.
     *
     * @param attributeBuffs Buffs to attributes that the item provides to the wielder.
     * @return Buffs to attributes that the item provides to the wielder.
     */
    private List<AttributeModifierTemplate> attributeBuffs = new ArrayList<>();

    /** Construct a new item stat template. */
    public ItemStatsTemplate() {}
}
