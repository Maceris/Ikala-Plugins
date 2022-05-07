package com.ikalagaming.item;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the item classes.
 *
 * @author Ches Burks
 *
 */
class ItemTest {

	/**
	 * Generate a random accessory, then convert to and from JSON to make sure
	 * it can be stored in the database properly.
	 */
	@Test
	void testAccessoryPersistence() {
		Gson gson = new Gson();
		Accessory accessory = ItemGenerator.getAccessory();
		Accessory parsed =
			gson.fromJson(gson.toJson(accessory), Accessory.class);
		Assertions.assertEquals(accessory, parsed);
	}

	/**
	 * Generate a random armor, then convert to and from JSON to make sure it
	 * can be stored in the database properly.
	 */
	@Test
	void testArmorPersistence() {
		Gson gson = new Gson();
		Armor armor = ItemGenerator.getArmor();
		Armor parsed = gson.fromJson(gson.toJson(armor), Armor.class);
		Assertions.assertEquals(armor, parsed);
	}

	/**
	 * Generate a random component, then convert to and from JSON to make sure
	 * it can be stored in the database properly.
	 */
	@Test
	void testComponentPersistence() {
		Gson gson = new Gson();
		Armor component = ItemGenerator.getArmor();
		Armor parsed = gson.fromJson(gson.toJson(component), Armor.class);
		Assertions.assertEquals(component, parsed);
	}

	/**
	 * Generate a random consumable, then convert to and from JSON to make sure
	 * it can be stored in the database properly.
	 */
	@Test
	void testConsumablePersistence() {
		Gson gson = new Gson();
		Consumable consumable = ItemGenerator.getConsumable();
		Consumable parsed =
			gson.fromJson(gson.toJson(consumable), Consumable.class);
		Assertions.assertEquals(consumable, parsed);
	}

	/**
	 * Generate a random junk item, then convert to and from JSON to make sure
	 * it can be stored in the database properly.
	 */
	@Test
	void testJunkPersistence() {
		Gson gson = new Gson();
		Item junk = ItemGenerator.getJunk();
		Item parsed = gson.fromJson(gson.toJson(junk), Item.class);
		Assertions.assertEquals(junk, parsed);
	}

	/**
	 * Generate a random material, then convert to and from JSON to make sure it
	 * can be stored in the database properly.
	 */
	@Test
	void testMaterialPersistence() {
		Gson gson = new Gson();
		Item material = ItemGenerator.getMaterial();
		Item parsed = gson.fromJson(gson.toJson(material), Item.class);
		Assertions.assertEquals(material, parsed);
	}

	/**
	 * Generate a random quest item, then convert to and from JSON to make sure
	 * it can be stored in the database properly.
	 */
	@Test
	void testQuestPersistence() {
		Gson gson = new Gson();
		Item quest = ItemGenerator.getQuest();
		Item parsed = gson.fromJson(gson.toJson(quest), Item.class);
		Assertions.assertEquals(quest, parsed);
	}

	/**
	 * Generate a random weapon, then convert to and from JSON to make sure it
	 * can be stored in the database properly.
	 */
	@Test
	void testWeaponPersistence() {
		Gson gson = new Gson();
		Weapon weapon = ItemGenerator.getWeapon();
		Weapon parsed = gson.fromJson(gson.toJson(weapon), Weapon.class);
		Assertions.assertEquals(weapon, parsed);
	}

}
