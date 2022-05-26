package com.ikalagaming.item;

import com.ikalagaming.item.enums.AccessoryType;
import com.ikalagaming.item.enums.ArmorType;
import com.ikalagaming.item.enums.ItemType;
import com.ikalagaming.item.enums.WeaponType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * What types of items something like a component or affix can be applied to.
 *
 * @author Ches Burks
 *
 */
@Getter
@Setter
@EqualsAndHashCode
public class ItemCriteria {

	/**
	 * The minimum item level requirement for this item.
	 *
	 * @param levelRequirement The item level requirement for this item.
	 * @return The item level requirement for this item.
	 */
	private Integer levelRequirement;

	/**
	 * The types of items that are relevant.
	 *
	 * @param itemTypes The categories of items that are relevant.
	 * @return The categories of items that are relevant.
	 */
	private List<ItemType> itemTypes = new ArrayList<>();

	/**
	 * The types of accessories that are relevant, populated only if accessory
	 * is included in the list of item types.
	 *
	 * @param accessoryTypes The types of accessories which are relevant.
	 * @return The types of accessories which are relevant.
	 */
	private List<AccessoryType> accessoryTypes = new ArrayList<>();

	/**
	 * The types of armor that are relevant, populated only if armor is included
	 * in the list of item types.
	 *
	 * @param armorTypes The types of armor which are relevant.
	 * @return The types of armor which are relevant.
	 */
	private List<ArmorType> armorTypes = new ArrayList<>();

	/**
	 * The types of weapon that are relevant, populated only if weapon is
	 * included in the list of item types.
	 *
	 * @param weaponTypes The types of weapon which are relevant.
	 * @return The types of weapon which are relevant.
	 */
	private List<WeaponType> weaponTypes = new ArrayList<>();

	/**
	 * Construct a new item criteria to specify what item types something like
	 * components or affixes can be applied to.
	 */
	public ItemCriteria() {}
}
