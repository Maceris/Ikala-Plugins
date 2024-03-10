package com.ikalagaming.attributes;

import lombok.Getter;

import java.util.EnumMap;
import java.util.Map;

/**
 * A block of stats for a character, including attributes and resistances.
 *
 * @author Ches Burks
 */
@Getter
public class StatBlock {
    /**
     * Attributes for the character.
     *
     * @return The map of attributes to values for a character.
     */
    private Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);

    /**
     * Resistances against different types of damage, as integer percentages.
     *
     * @return The map from damage type to resistance as an integer percentage.
     */
    private Map<DamageType, Integer> resistances = new EnumMap<>(DamageType.class);
}
