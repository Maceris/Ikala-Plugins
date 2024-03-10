package com.ikalagaming.attributes;

/**
 * The types of damage that exist, which are also used in resistances.
 *
 * @author Ches Burks
 */
public enum DamageType {
    /** Physical damage, bludgeoning, slashing. */
    PHYSICAL,
    /** Damage from heat, fire. */
    FIRE,
    /** Damage from cold, ice. */
    COLD,
    /** Damage from electricity, lightning. */
    ELECTRIC,
    /** Damage from physical or magical forces, thunder. */
    FORCE,
    /** Damage from acids, corrosive materials, physically dissolving. */
    ACID,
    /** Damage from toxins, poisoning. */
    POISON,
    /** Damage from stabbing, piercing. Generally ignores most armor. */
    PIERCING,
    /** Mental damage. */
    PSYCHIC,
    /** Damage of a divine nature, like smiting or the sun burning a vampire. */
    HOLY,
    /** Damage from dark magic, vampiric magic, necrotic or corrupting attacks. */
    UNHOLY;
}
