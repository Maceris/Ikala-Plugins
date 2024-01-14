package com.ikalagaming.rpg.item;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Stats that belong to an item that describes what it provides to the user.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
@EqualsAndHashCode
public class ItemStats {
	/**
	 * Buffs to damage done by the wielder of this item.
	 *
	 * @param damageBuffs Buffs to damage done by the wielder of this item.
	 * @return Buffs to damage done by the wielder of this item.
	 */
	private List<DamageModifier> damageBuffs = new ArrayList<>();

	/**
	 * Buffs to damage resistance that the item provides to the wielder.
	 *
	 * @param resistanceBuffs Damage resistance that the item provides to the
	 *            wielder.
	 * @return Damage resistance that the item provides to the wielder.
	 */
	private List<DamageModifier> resistanceBuffs = new ArrayList<>();
	/**
	 * Buffs to attributes that the item provides to the wielder.
	 *
	 * @param attributeBuffs Buffs to attributes that the item provides to the
	 *            wielder.
	 * @return Buffs to attributes that the item provides to the wielder.
	 */
	private List<AttributeModifier> attributeBuffs = new ArrayList<>();

	/**
	 * Construct a new item stat object.
	 */
	public ItemStats() {}
}
