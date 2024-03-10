package com.ikalagaming.rpg.item;

import com.ikalagaming.attributes.Attribute;
import com.ikalagaming.rpg.item.enums.ModifierType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Tracks modification of an attribute.
 *
 * @author Ches Burks
 */
@EqualsAndHashCode
@Getter
@Setter
@ToString
public class AttributeModifier {
    /**
     * The specific attribute this modifier affects.
     *
     * @param attribute The attribute this modifies.
     * @return The attribute this modifies.
     */
    private Attribute attribute;

    /**
     * How this modifier affects the base value, such as a flat change, percentage difference, or a
     * range of possible values.
     *
     * @param type The way that this affects the base value of the stat/concept it modifies.
     * @return The way that this affects the base value of the stat/concept it modifies.
     */
    private ModifierType type;

    /**
     * The amount the attribute is modified by, where the type decides if this is used as a flat
     * number or percentage.
     *
     * @param amount The new amount.
     * @return The amount the attribute is modified by.
     */
    private Integer amount;

    /** Construct a new attribute modifier. */
    public AttributeModifier() {}

    /**
     * Make a clone of this modifier.
     *
     * @return The copy with the same values.
     */
    AttributeModifier copy() {
        AttributeModifier clone = new AttributeModifier();
        clone.setAttribute(getAttribute());
        clone.setType(getType());
        clone.setAmount(getAmount());
        return clone;
    }
}
