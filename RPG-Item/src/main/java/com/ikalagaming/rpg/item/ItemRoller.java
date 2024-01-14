package com.ikalagaming.rpg.item;

import com.ikalagaming.rpg.item.template.AccessoryTemplate;
import com.ikalagaming.rpg.item.template.ArmorTemplate;
import com.ikalagaming.rpg.item.template.AttributeModifierTemplate;
import com.ikalagaming.rpg.item.template.DamageModifierTemplate;
import com.ikalagaming.rpg.item.template.EquipmentTemplate;
import com.ikalagaming.rpg.item.template.ItemStatsTemplate;
import com.ikalagaming.rpg.item.template.WeaponTemplate;

import java.util.SplittableRandom;

/**
 * Used to generate random items based on a template.
 *
 * @author Ches Burks
 *
 */
public class ItemRoller {
	private static SplittableRandom rand = new SplittableRandom();

	/**
	 * Copies both item fields, and equipment fields, into the first object from
	 * the second. Rolls fields as required.
	 *
	 * @param modified The item to copy item and equipment fields into, which is
	 *            modified.
	 * @param template The item to copy from, not modified.
	 */
	private static void copyEquipmentFields(Equipment modified,
		final EquipmentTemplate template) {
		ItemRoller.copyItemFields(modified, template);
		template.getAttributeRequirements()
			.forEach(modifier -> modified.getAttributeRequirements()
				.add(ItemRoller.rollAttributeModifier(modifier)));
		modified.setLevelRequirement(template.getLevelRequirement());
		modified.setItemStats(
			ItemRoller.rollItemStats(template.getItemStatsTemplate()));
	}

	/**
	 * Copy the item fields into the first object from the second one.
	 *
	 * @param modified The item to copy fields into, which is modified.
	 * @param template The item to copy from, not modified.
	 */
	private static void copyItemFields(Item modified, final Item template) {
		modified.setID(template.getID());
		modified.setItemLevel(template.getItemLevel());
		modified.setItemType(template.getItemType());
		modified.setQuality(template.getQuality());
	}

	/**
	 * Roll an accessory based on an accessory template.
	 *
	 * @param template The template we are generating an accessory from.
	 * @return The newly generated accessory.
	 */
	public static Accessory rollAccessory(AccessoryTemplate template) {
		Accessory accessory = new Accessory();
		ItemRoller.copyEquipmentFields(accessory, template);
		accessory.setAccessoryType(template.getAccessoryType());
		return accessory;
	}

	/**
	 * Generate an amount +/- some variance. Variance should be between 1 and
	 * amount, we will take the absolute value of the variance if negative.
	 *
	 * @param amount The amount.
	 * @param variance The distance from the amount we can generate values
	 *            within.
	 * @return The random amount given the base amount and variance.
	 */
	private static int rollAmount(final int amount, final int variance) {
		final int absVariance = Math.abs(variance);
		if (absVariance <= 0) {
			return amount;
		}
		return amount + ItemRoller.rand.nextInt(2 * absVariance) - absVariance;
	}

	/**
	 * Roll armor based on a template.
	 *
	 * @param template The template to generate armor from.
	 * @return The newly generated armor.
	 */
	public static Armor rollArmor(ArmorTemplate template) {
		Armor armor = new Armor();
		ItemRoller.copyEquipmentFields(armor, template);
		armor.setArmorType(template.getArmorType());
		return armor;
	}

	/**
	 * Roll an attribute modifier based on the template.
	 *
	 * @param template The template we are generating a modifier from.
	 * @return The newly generated attribute modifier.
	 */
	private static AttributeModifier
		rollAttributeModifier(AttributeModifierTemplate template) {
		AttributeModifier modifier = new AttributeModifier();
		modifier.setAmount(ItemRoller.rollAmount(template.getAmount(),
			template.getVariance()));
		modifier.setAttribute(template.getAttribute());
		modifier.setType(template.getType());
		return modifier;
	}

	/**
	 * Roll a damage modifier based on the template.
	 *
	 * @param template The template we are generating a modifier from.
	 * @return The newly generated damage modifier.
	 */
	private static DamageModifier
		rollDamageModifier(DamageModifierTemplate template) {
		DamageModifier modifier = new DamageModifier();
		modifier.setAmount(ItemRoller.rollAmount(template.getAmount(),
			template.getVariance()));
		modifier.setDamageType(template.getDamageType());
		modifier.setType(template.getType());
		return template;
	}

	/**
	 * Roll item stats based on an item stats template.
	 *
	 * @param template The template we are generating item stats from.
	 * @return The newly generated item stats block.
	 */
	private static ItemStats rollItemStats(ItemStatsTemplate template) {
		ItemStats stats = new ItemStats();
		template.getAttributeBuffs()
			.forEach(modifier -> stats.getAttributeBuffs()
				.add(ItemRoller.rollAttributeModifier(modifier)));
		template.getDamageBuffs().forEach(modifier -> stats.getDamageBuffs()
			.add(ItemRoller.rollDamageModifier(modifier)));
		template.getResistanceBuffs().forEach(modifier -> stats
			.getResistanceBuffs().add(ItemRoller.rollDamageModifier(modifier)));
		return stats;
	}

	/**
	 * Roll a weapon based on a template.
	 *
	 * @param template The template to generate a weapon from.
	 * @return The newly generated weapon.
	 */
	public static Weapon rollWeapon(WeaponTemplate template) {
		Weapon weapon = new Weapon();
		ItemRoller.copyEquipmentFields(weapon, template);
		weapon.setMaxDamage(template.getMaxDamage());
		weapon.setMinDamage(template.getMinDamage());
		weapon.setWeaponType(template.getWeaponType());
		weapon.setDamageType(template.getDamageType());
		return weapon;
	}

	/**
	 * Private constructor so this is not instantiated.
	 */
	private ItemRoller() {
		throw new UnsupportedOperationException(
			"This utility class should not be instantiated");
	}

}
