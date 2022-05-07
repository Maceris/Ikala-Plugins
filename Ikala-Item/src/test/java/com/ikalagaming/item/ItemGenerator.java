package com.ikalagaming.item;

import com.ikalagaming.attributes.Attribute;
import com.ikalagaming.attributes.DamageType;
import com.ikalagaming.item.template.AttributeModifierTemplate;
import com.ikalagaming.item.template.DamageModifierTemplate;
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
		attribute.setAmount(Math.abs(ItemGenerator.rand.nextInt(100)));
		attribute
			.setVariance(ItemGenerator.rand.nextInt(attribute.getAmount()));
		return attribute;
	}

	/**
	 * Generate a random damage modifier template for use in generating random
	 * item templates.
	 *
	 * @return The randomly generated damage modifier template.
	 */
	private static DamageModifierTemplate getDamageModifierTemplate() {
		DamageModifierTemplate damage = new DamageModifierTemplate();
		damage.setAmount(Math.abs(ItemGenerator.rand.nextInt(100)));
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
		weapon.setType(ItemType.WEAPON);
		weapon.setWeaponType(
			ItemGenerator.random.selectEnumValue(WeaponType.class));
		weapon.setQuality(ItemGenerator.random.selectEnumValue(Quality.class));
		weapon.setItemLevel(Math.abs(ItemGenerator.rand.nextInt(100)));
		weapon.setID(
			weapon.getType().getPrefix() + weapon.getWeaponType().getPrefix()
				+ ItemGenerator.nextID.getAndIncrement());

		// Equipment traits
		final int maxRequirements = ItemGenerator.rand.nextInt(4) + 1;
		for (int i = 0; i < maxRequirements; ++i) {
			weapon.getAttributeRequirements()
				.add(ItemGenerator.getAttributeModifierTemplate());
		}
		weapon.setLevelRequirement(Math.abs(ItemGenerator.rand.nextInt(100)));
		weapon.setItemStats(ItemGenerator.getItemStatsTemplate());

		// weapon traits
		weapon.setMaxDamage(Math.abs(ItemGenerator.rand.nextInt(100)));
		weapon.setMinDamage(ItemGenerator.rand.nextInt(weapon.getMaxDamage()));

		return weapon;
	}
}
