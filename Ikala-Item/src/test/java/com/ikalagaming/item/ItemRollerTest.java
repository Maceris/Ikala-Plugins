package com.ikalagaming.item;

import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.ArmorTemplate;
import com.ikalagaming.item.template.AttributeModifierTemplate;
import com.ikalagaming.item.template.DamageModifierTemplate;
import com.ikalagaming.item.template.EquipmentTemplate;
import com.ikalagaming.item.template.ItemStatsTemplate;
import com.ikalagaming.item.template.WeaponTemplate;
import com.ikalagaming.item.testing.ItemGenerator;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for the item classes.
 *
 * @author Ches Burks
 *
 */
class ItemRollerTest {

	private static Gson gson = new Gson();

	/**
	 * Check if the given attribute might have been generated from the template.
	 *
	 * @param template The template the modifier might have been generated from.
	 * @param actual The actual modifier.
	 * @return Whether or not the modifier could have been generated from the
	 *         template.
	 */
	private static boolean attributeModifierValid(
		final AttributeModifierTemplate template,
		final AttributeModifier actual) {
		if (!template.getType().equals(actual.getType())
			|| !template.getAttribute().equals(actual.getAttribute())) {
			return false;
		}

		final int amount = actual.getAmount();
		final int base = template.getAmount();
		final int variance = template.getVariance();

		if (amount > base) {
			return amount <= base + variance;
		}
		if (amount < base) {
			return amount >= base - variance;
		}
		// amount is equal to the base
		return true;
	}

	/**
	 * Check if the given attribute might have been generated from the template.
	 *
	 * @param template The template the modifier might have been generated from.
	 * @param actual The actual modifier.
	 * @return Whether or not the modifier could have been generated from the
	 *         template.
	 */
	private static boolean damageModifierValid(
		final DamageModifierTemplate template, final DamageModifier actual) {
		if (!template.getType().equals(actual.getType())
			|| !template.getDamageType().equals(actual.getDamageType())) {
			return false;
		}

		final int amount = actual.getAmount();
		final int base = template.getAmount();
		final int variance = template.getVariance();

		if (amount > base) {
			return amount <= base + variance;
		}
		if (amount < base) {
			return amount >= base - variance;
		}
		// amount is equal to the base
		return true;
	}

	/**
	 * Check that the list of modifier templates could have generated the actual
	 * list of modifiers.
	 *
	 * @param template The template list.
	 * @param actual The actual list.
	 */
	private static void matchAttributeTemplateLists(
		List<AttributeModifierTemplate> template,
		List<AttributeModifier> actual) {
		// Check attributes match the requirements
		Assertions.assertEquals(template.size(), actual.size());
		// For each template, there exists a valid attribute that matches it
		Assertions.assertTrue(
			template.stream()
				.allMatch(expectedAttribute -> actual.stream()
					.anyMatch(actualAttribute -> ItemRollerTest
						.attributeModifierValid(expectedAttribute,
							actualAttribute))),
			"Expected everything in " + gson.toJson(template) + " to be in "
				+ gson.toJson(actual));
		// For each attribute, there exists a template that could generate it
		Assertions.assertTrue(
			actual.stream()
				.allMatch(actualAttribute -> template.stream()
					.anyMatch(expectedAttribute -> ItemRollerTest
						.attributeModifierValid(expectedAttribute,
							actualAttribute))),
			"Expected everything in " + gson.toJson(actual) + " to be in "
				+ gson.toJson(template));
	}

	/**
	 * Check that the list of modifier templates could have generated the actual
	 * list of modifiers.
	 *
	 * @param template The template list.
	 * @param actual The actual list.
	 */
	private static void matchDamageTemplateLists(
		List<DamageModifierTemplate> template, List<DamageModifier> actual) {
		// Check attributes match the requirements
		Assertions.assertEquals(template.size(), actual.size());
		// For each template, there exists a valid attribute that matches it
		Assertions.assertTrue(template.stream()
			.allMatch(expectedDamage -> actual.stream()
				.anyMatch(actualDamage -> ItemRollerTest
					.damageModifierValid(expectedDamage, actualDamage))));
		// For each attribute, there exists a template that could generate it
		Assertions.assertTrue(actual.stream()
			.allMatch(actualDamage -> template.stream()
				.anyMatch(expectedDamage -> ItemRollerTest
					.damageModifierValid(expectedDamage, actualDamage))));
	}

	/**
	 * Match equipment fields to make sure they could ahve been generated from
	 * the template.
	 *
	 * @param template The template for generating the equipment.
	 * @param actual The actual equipment.
	 */
	private static void matchEquipmentFields(EquipmentTemplate template,
		Equipment actual) {
		ItemRollerTest.matchItemFields(template, actual);
		Assertions.assertEquals(template.getLevelRequirement(),
			actual.getLevelRequirement());

		ItemRollerTest.matchAttributeTemplateLists(
			template.getAttributeRequirements(),
			actual.getAttributeRequirements());

		ItemRollerTest.matchItemStats(template.getItemStatsTemplate(),
			actual.getItemStats());
	}

	/**
	 * Match the item fields between the two items.
	 *
	 * @param expected The expected item.
	 * @param actual The actual item.
	 */
	private static void matchItemFields(Item expected, Item actual) {
		Assertions.assertEquals(expected.getItemType(), actual.getItemType());
		Assertions.assertEquals(expected.getID(), actual.getID());
		Assertions.assertEquals(expected.getQuality(), actual.getQuality());
		Assertions.assertEquals(expected.getItemLevel(), actual.getItemLevel());
	}

	/**
	 * Match the item stats template to make sure the actual stats could have
	 * been generated from them.
	 *
	 * @param template The stats template.
	 * @param actual The actual stats.
	 */
	private static void matchItemStats(ItemStatsTemplate template,
		ItemStats actual) {
		ItemRollerTest.matchAttributeTemplateLists(template.getAttributeBuffs(),
			actual.getAttributeBuffs());
		ItemRollerTest.matchDamageTemplateLists(template.getDamageBuffs(),
			actual.getDamageBuffs());
		ItemRollerTest.matchDamageTemplateLists(template.getResistanceBuffs(),
			actual.getResistanceBuffs());
	}

	/**
	 * Roll an accessory from template and make sure the results are reasonable.
	 */
	@Test
	void testRollAccessory() {
		AccessoryTemplate template = ItemGenerator.getAccessoryTemplate();
		Accessory accessory = ItemRoller.rollAccessory(template);
		ItemRollerTest.matchEquipmentFields(template, accessory);
		Assertions.assertEquals(template.getAccessoryType(),
			accessory.getAccessoryType());
	}

	/**
	 * Roll armor from template and make sure the results are reasonable.
	 */
	@Test
	void testRollArmor() {
		ArmorTemplate template = ItemGenerator.getArmorTemplate();
		Armor armor = ItemRoller.rollArmor(template);
		ItemRollerTest.matchEquipmentFields(template, armor);
		Assertions.assertEquals(template.getArmorType(), armor.getArmorType());
	}

	/**
	 * Roll a weapon from template and make sure the results are reasonable.
	 */
	@Test
	void testRollWeapon() {
		WeaponTemplate template = ItemGenerator.getWeaponTemplate();
		Weapon weapon = ItemRoller.rollWeapon(template);
		ItemRollerTest.matchEquipmentFields(template, weapon);
		Assertions.assertEquals(template.getWeaponType(),
			weapon.getWeaponType());
		Assertions.assertEquals(template.getMinDamage(), weapon.getMinDamage());
		Assertions.assertEquals(template.getMaxDamage(), weapon.getMaxDamage());
	}

}
