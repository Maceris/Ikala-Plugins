package com.ikalagaming.item;

import com.ikalagaming.attributes.Attribute;
import com.ikalagaming.attributes.DamageType;
import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.ArmorTemplate;
import com.ikalagaming.item.template.AttributeModifierTemplate;
import com.ikalagaming.item.template.ComponentTemplate;
import com.ikalagaming.item.template.DamageModifierTemplate;
import com.ikalagaming.item.template.EquipmentTemplate;
import com.ikalagaming.item.template.ItemStatsTemplate;
import com.ikalagaming.item.template.WeaponTemplate;
import com.ikalagaming.random.RandomGen;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used to generate random items and templates for testing.
 *
 * @author Ches Burks
 *
 */
class ItemGenerator {
	private static Random rand = new Random();
	private static RandomGen random = new RandomGen();
	private static AtomicInteger nextID = new AtomicInteger();

	/**
	 * Generate a random accessory template.
	 *
	 * @return The randomly generated accessory template.
	 */
	public static AccessoryTemplate getAccessoryTemplate() {
		AccessoryTemplate accessory = new AccessoryTemplate();

		accessory.setItemType(ItemType.ACCESSORY);
		accessory.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		accessory
			.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		accessory.setAccessoryType(
			ItemGenerator.random.selectEnumValue(AccessoryType.class));
		accessory.setID(accessory.getItemType().getPrefix()
			+ accessory.getAccessoryType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		ItemGenerator.populateEquipment(accessory);
		return accessory;
	}

	/**
	 * Generate a random armor template.
	 *
	 * @return The randomly generated armor template.
	 */
	public static ArmorTemplate getArmorTemplate() {
		ArmorTemplate armor = new ArmorTemplate();

		armor.setItemType(ItemType.ARMOR);
		armor.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		armor.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		armor.setArmorType(
			ItemGenerator.random.selectEnumValue(ArmorType.class));
		armor.setID(
			armor.getItemType().getPrefix() + armor.getArmorType().getPrefix()
				+ ItemGenerator.nextID.getAndIncrement());
		ItemGenerator.populateEquipment(armor);
		return armor;
	}

	/**
	 * Generates a random attribute modifier template for use in generating
	 * random item templates.
	 *
	 * @return The randomly generated attribute modifier template.
	 */
	private static AttributeModifierTemplate getAttributeModifierTemplate() {
		AttributeModifierTemplate attribute = new AttributeModifierTemplate();
		attribute.setAttribute(
			ItemGenerator.random.selectEnumValue(Attribute.class));
		attribute
			.setType(ItemGenerator.random.selectEnumValue(ModifierType.class));
		attribute.setAmount(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		attribute
			.setVariance(ItemGenerator.rand.nextInt(attribute.getAmount()));
		return attribute;
	}

	/**
	 * Generate a random component template.
	 *
	 * @return The randomly generated component template.
	 */
	public static ComponentTemplate getComponentTemplate() {
		ComponentTemplate component = new ComponentTemplate();

		component.setItemType(ItemType.ARMOR);
		component.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		component
			.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		component.setComponentType(
			ItemGenerator.random.selectEnumValue(ComponentType.class));
		component.setID(component.getItemType().getPrefix()
			+ component.getComponentType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());
		component.setItemStats(ItemGenerator.getItemStatsTemplate());

		return component;
	}

	/**
	 * Generate a random damage modifier template for use in generating random
	 * item templates.
	 *
	 * @return The randomly generated damage modifier template.
	 */
	private static DamageModifierTemplate getDamageModifierTemplate() {
		DamageModifierTemplate damage = new DamageModifierTemplate();
		damage.setAmount(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		damage.setDamageType(
			ItemGenerator.random.selectEnumValue(DamageType.class));
		damage
			.setType(ItemGenerator.random.selectEnumValue(ModifierType.class));
		damage.setVariance(ItemGenerator.rand.nextInt(damage.getAmount()));
		return damage;
	}

	/**
	 * Generate a random item stats template for use in equipment templates.
	 *
	 * @return The randomly generated item stats template.
	 */
	private static ItemStatsTemplate getItemStatsTemplate() {
		ItemStatsTemplate template = new ItemStatsTemplate();
		final int maxDamages = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxDamages; ++i) {
			template.getDamageBuffs()
				.add(ItemGenerator.getDamageModifierTemplate());
		}
		final int maxBuffs = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxBuffs; ++i) {
			template.getResistanceBuffs()
				.add(ItemGenerator.getDamageModifierTemplate());
		}
		final int maxAttributes = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxAttributes; ++i) {
			template.getAttributeBuffs()
				.add(ItemGenerator.getAttributeModifierTemplate());
		}
		return template;
	}

	/**
	 * Generate a random weapon template. All fields should have reasonable
	 * values.
	 *
	 * @return A randomly generated weapon template.
	 */
	public static WeaponTemplate getWeaponTemplate() {
		WeaponTemplate weapon = new WeaponTemplate();

		// Item traits, plus weapon type so we can name it properly
		weapon.setItemType(ItemType.WEAPON);
		weapon.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		weapon.setWeaponType(
			ItemGenerator.random.selectEnumValue(WeaponType.class));
		weapon.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		weapon.setID(weapon.getItemType().getPrefix()
			+ weapon.getWeaponType().getPrefix()
			+ ItemGenerator.nextID.getAndIncrement());

		ItemGenerator.populateEquipment(weapon);

		// weapon traits
		weapon.setMaxDamage(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		weapon.setMinDamage(ItemGenerator.rand.nextInt(weapon.getMaxDamage()));

		return weapon;
	}

	/**
	 * Fill out fields for equipment.
	 *
	 * @param template The item template to fill out fields for.
	 */
	private static void populateEquipment(EquipmentTemplate template) {
		final int maxRequirements = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxRequirements; ++i) {
			template.getAttributeRequirements()
				.add(ItemGenerator.getAttributeModifierTemplate());
		}
		template
			.setLevelRequirement(Math.abs(ItemGenerator.rand.nextInt(99)) + 1);
		template.setItemStats(ItemGenerator.getItemStatsTemplate());
	}
}
