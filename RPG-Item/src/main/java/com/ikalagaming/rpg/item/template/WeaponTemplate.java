package com.ikalagaming.rpg.item.template;

import com.ikalagaming.attributes.DamageType;
import com.ikalagaming.rpg.item.enums.WeaponType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

/**
 * A template for generating weapons from.
 *
 * @author Ches Burks
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = WeaponTemplate.TABLE_NAME)
public class WeaponTemplate extends EquipmentTemplate {
    /** The name of the table in the database. */
    static final String TABLE_NAME = "WEAPON_TEMPLATE";

    /**
     * The type of damage the weapon does.
     *
     * @param damageType The type of damage the weapon does.
     * @return The type of damage the weapon does.
     */
    @Column(name = "DAMAGE_TYPE")
    @Enumerated(EnumType.STRING)
    private DamageType damageType;

    /**
     * The minimum damage that this weapon does, usually damage done per hit is randomly distributed
     * within a range of values.
     *
     * @param minDamage The lower bound of default damage for this type of weapon.
     * @return The lower bound of default damage for this type of weapon.
     */
    @Column(name = "MIN_DAMAGE")
    private Integer minDamage;

    /**
     * The maximum damage that this weapon does, usually damage done per hit is randomly distributed
     * within a range of values.
     *
     * @param maxDamage The upper bound of default damage for this type of weapon.
     * @return The upper bound of default damage for this type of weapon.
     */
    @Column(name = "MAX_DAMAGE")
    private Integer maxDamage;

    /**
     * What kind of weapon this is.
     *
     * @param weaponType The classification of weapon.
     * @return The classification of weapon.
     */
    @Column(name = "WEAPON_TYPE")
    @Enumerated(EnumType.STRING)
    private WeaponType weaponType;

    /** Construct a new weapon template. */
    public WeaponTemplate() {}
}
