package com.ikalagaming.item;

import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.WeaponTemplate;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the weapon template class.
 *
 * @author Ches Burks
 *
 */
class TemplateTest {

	/**
	 * Generate a random weapon, then convert to and from json to make sure it
	 * can be stored in the database properly.
	 */
	@Test
	void testWeaponPersistence() {
		Gson gson = new Gson();
		WeaponTemplate template = ItemGenerator.getWeaponTemplate();
		WeaponTemplate parsed =
			gson.fromJson(gson.toJson(template), WeaponTemplate.class);
		Assertions.assertEquals(template, parsed);
	}

	/**
	 * Generate a random weapon, then convert to and from json to make sure it
	 * can be stored in the database properly.
	 */
	@Test
	void testAccessoryPersistence() {
		Gson gson = new Gson();
		AccessoryTemplate template = ItemGenerator.getAccessoryTemplate();
		AccessoryTemplate parsed =
			gson.fromJson(gson.toJson(template), AccessoryTemplate.class);
		Assertions.assertEquals(template, parsed);
	}

}
