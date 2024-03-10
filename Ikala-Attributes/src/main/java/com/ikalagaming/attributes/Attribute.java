package com.ikalagaming.attributes;

/**
 * The different attributes that exist for characters.
 *
 * @author Ches Burks
 */
public enum Attribute {
    /**
     * Increases physical and elemental damage for all weapons, increases critical strike damage
     * bonus amount. Requirement for many forms of armor or larger weapons.
     */
    STRENGTH,
    /**
     * Increases magical damage, magic regeneration. Requirement for many magical weapons and
     * abilities.
     */
    INTELLIGENCE,
    /** Increases maximum health, health regeneration, resistance to damage. */
    CONSTITUTION,
    /**
     * Increases dodge chance, critical strike chance, attack speed. Requirement for smaller or more
     * precise weapons.
     */
    DEXTERITY,
    /** A measure of attractiveness, persuasiveness, leadership, force of personality. */
    CHARISMA,
    /** Luck that affects random items, encounters, critical hits. */
    LUCK;
}
