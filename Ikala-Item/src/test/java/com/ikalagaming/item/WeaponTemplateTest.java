package com.ikalagaming.item;

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
class WeaponTemplateTest {

	/**
	 * Generate a random weapon, then convert to and from json to make sure it
	 * can be stored in the database properly.
	 */
	@Test
	void testPersistence() {
		Gson gson = new Gson();
		WeaponTemplate template = ItemGenerator.getWeaponTemplate();
		WeaponTemplate parsed =
			gson.fromJson(gson.toJson(template), WeaponTemplate.class);
		Assertions.assertEquals(template, parsed);
	}

}
