package com.ikalagaming.item;

import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.ArmorTemplate;
import com.ikalagaming.item.template.WeaponTemplate;
import com.ikalagaming.item.testing.ItemGenerator;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the item template classes.
 *
 * @author Ches Burks
 *
 */
class TemplateTest {

	/**
	 * Generate a random accessory template, then convert to and from json to
	 * make sure it can be stored in the database properly.
	 */
	@Test
	void testAccessoryTemplatePersistence() {
		Gson gson = new Gson();
		AccessoryTemplate template = ItemGenerator.getAccessoryTemplate();
		AccessoryTemplate parsed =
			gson.fromJson(gson.toJson(template), AccessoryTemplate.class);
		Assertions.assertEquals(template, parsed);
	}

	/**
	 * Generate a random armor template, then convert to and from json to make
	 * sure it can be stored in the database properly.
	 */
	@Test
	void testArmorTemplatePersistence() {
		Gson gson = new Gson();
		ArmorTemplate template = ItemGenerator.getArmorTemplate();
		ArmorTemplate parsed =
			gson.fromJson(gson.toJson(template), ArmorTemplate.class);
		Assertions.assertEquals(template, parsed);
	}

	/**
	 * Generate a random component template, then convert to and from json to
	 * make sure it can be stored in the database properly.
	 */
	@Test
	void testComponentTemplatePersistence() {
		Gson gson = new Gson();
		ArmorTemplate template = ItemGenerator.getArmorTemplate();
		ArmorTemplate parsed =
			gson.fromJson(gson.toJson(template), ArmorTemplate.class);
		Assertions.assertEquals(template, parsed);
	}

	/**
	 * Generate a random weapon template, then convert to and from json to make
	 * sure it can be stored in the database properly.
	 */
	@Test
	void testWeaponTemplatePersistence() {
		Gson gson = new Gson();
		WeaponTemplate template = ItemGenerator.getWeaponTemplate();
		WeaponTemplate parsed =
			gson.fromJson(gson.toJson(template), WeaponTemplate.class);
		Assertions.assertEquals(template, parsed);
	}

}
