package com.ikalagaming.item;

import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.ArmorTemplate;
import com.ikalagaming.item.template.AttributeModifierTemplate;
import com.ikalagaming.item.template.ComponentTemplate;
import com.ikalagaming.item.template.DamageModifierTemplate;
import com.ikalagaming.item.template.EquipmentTemplate;
import com.ikalagaming.item.template.ItemStatsTemplate;
import com.ikalagaming.item.template.WeaponTemplate;

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
	private void copyEquipmentFields(Equipment modified,
		final EquipmentTemplate template) {
		this.copyItemFields(modified, template);
		template.getAttributeRequirements()
			.forEach(modifier -> modified.getAttributeRequirements()
				.add(this.rollAttributeModifier(modifier)));
		modified.setLevelRequirement(template.getLevelRequirement());
		modified
			.setItemStats(this.rollItemStats(template.getItemStatsTemplate()));
	}

	/**
	 * Copy the item fields into the first object from the second one.
	 *
	 * @param modified The item to copy fields into, which is modified.
	 * @param template The item to copy from, not modified.
	 */
	private void copyItemFields(Item modified, final Item template) {
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
	public Accessory rollAccessory(AccessoryTemplate template) {
		Accessory accessory = new Accessory();
		this.copyEquipmentFields(accessory, template);
		accessory.setAccessoryType(template.getAccessoryType());
		return accessory;
	}

	/**
	 * Generate an amount +/- some variance. Variance should be between 0 and
	 * amount, inclusive, if you want it to behave as expected.
	 *
	 * @param amount The amount.
	 * @param variance The distance from the amount we can generate values
	 *            within.
	 * @return The random amount given the base amount and variance.
	 */
	private int rollAmount(final int amount, final int variance) {
		return amount + ItemRoller.rand.nextInt(2 * variance) - variance;
	}

	/**
	 * Roll armor based on a template.
	 *
	 * @param template The template to generate armor from.
	 * @return The newly generated armor.
	 */
	public Armor rollArmor(ArmorTemplate template) {
		Armor armor = new Armor();
		this.copyEquipmentFields(armor, template);
		armor.setArmorType(template.getArmorType());
		return armor;
	}

	/**
	 * Roll an attribute modifier based on the template.
	 *
	 * @param template The template we are generating a modifier from.
	 * @return The newly generated attribute modifier.
	 */
	private AttributeModifier
		rollAttributeModifier(AttributeModifierTemplate template) {

		AttributeModifier modifier = new AttributeModifier();
		modifier.setAmount(
			this.rollAmount(template.getAmount(), template.getVariance()));
		modifier.setAttribute(template.getAttribute());
		modifier.setType(template.getType());
		return modifier;
	}

	/**
	 * Roll a component based on a template.
	 *
	 * @param template The template to generate a component from.
	 * @return The newly generated component.
	 */
	public Component rollComponent(ComponentTemplate template) {
		Component component = new Component();
		this.copyItemFields(component, template);
		component.setComponentType(template.getComponentType());
		component.setItemStats(this.rollItemStats(template.getItemStats()));
		return component;
	}

	/**
	 * Roll a damage modifier based on the template.
	 *
	 * @param template The template we are generating a modifier from.
	 * @return The newly generated damage modifier.
	 */
	private DamageModifier rollDamageModifier(DamageModifierTemplate template) {
		DamageModifier modifier = new DamageModifier();
		modifier.setAmount(
			this.rollAmount(template.getAmount(), template.getVariance()));
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
	private ItemStats rollItemStats(ItemStatsTemplate template) {
		ItemStats stats = new ItemStats();
		template.getAttributeBuffs().forEach(modifier -> stats
			.getAttributeBuffs().add(this.rollAttributeModifier(modifier)));
		template.getDamageBuffs().forEach(modifier -> stats.getDamageBuffs()
			.add(this.rollDamageModifier(modifier)));
		template.getResistanceBuffs().forEach(modifier -> stats
			.getResistanceBuffs().add(this.rollDamageModifier(modifier)));
		return stats;
	}

	/**
	 * Roll a weapon based on a template.
	 *
	 * @param template The template to generate a weapon from.
	 * @return The newly generated weapon.
	 */
	public Weapon rollWeapon(WeaponTemplate template) {
		Weapon weapon = new Weapon();
		this.copyEquipmentFields(weapon, template);
		weapon.setMaxDamage(template.getMaxDamage());
		weapon.setMinDamage(template.getMinDamage());
		weapon.setWeaponType(template.getWeaponType());
		return weapon;
	}

}
