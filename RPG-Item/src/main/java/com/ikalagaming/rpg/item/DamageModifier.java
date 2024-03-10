package com.ikalagaming.rpg.item;

import com.ikalagaming.attributes.DamageType;
import com.ikalagaming.rpg.item.enums.ModifierType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Tracks modification of damage, or damage resistance.
 *
 * @author Ches Burks
 */
@EqualsAndHashCode
@Getter
@Setter
@ToString
public class DamageModifier {
    /**
     * The type of damage this modifier affects.
     *
     * @param damageType The damage type this modifies.
     * @return The damage type this modifies.
     */
    private DamageType damageType;

    /**
     * How this modifier affects the base value, such as a flat change, percentage difference, or a
     * range of possible values.
     *
     * @param type The way that this affects the base value of the stat/concept it modifies.
     * @return The way that this affects the base value of the stat/concept it modifies.
     */
    private ModifierType type;

    /**
     * The amount the damage is or is modified by, where the type decides if this is used as a flat
     * number or percentage.
     *
     * @param amount The new amount.
     * @return The amount of the damage or damage modification.
     */
    private Integer amount;

    /**
     * Make a clone of this modifier.
     *
     * @return The copy with the same values.
     */
    DamageModifier copy() {
        DamageModifier clone = new DamageModifier();
        clone.setDamageType(getDamageType());
        clone.setType(getType());
        clone.setAmount(getAmount());
        return clone;
    }
}
