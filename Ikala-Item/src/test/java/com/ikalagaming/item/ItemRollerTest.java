package com.ikalagaming.item;

import com.ikalagaming.item.template.AccessoryTemplate;
import com.ikalagaming.item.template.EquipmentTemplate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the item classes.
 *
 * @author Ches Burks
 *
 */
class ItemRollerTest {

	private void matchItemFields(Item expected, Item actual) {
		Assertions.assertEquals(expected.getItemType(), actual.getItemType());
		Assertions.assertEquals(expected.getID(), actual.getID());
		Assertions.assertEquals(expected.getQuality(), actual.getQuality());
		Assertions.assertEquals(expected.getItemLevel(), actual.getItemLevel());
	}

	private void matchEquipmentFields(EquipmentTemplate expected,
		Equipment actual) {
		Assertions.assertEquals(expected.getLevelRequirement(),
			actual.getLevelRequirement());
		
		// attribute requirements
		// item stats
	}

	@Test
	void testRollAccessory() {
		AccessoryTemplate template = ItemGenerator.getAccessoryTemplate();
		Accessory accessory = ItemRoller.rollAccessory(template);
		matchItemFields(template, accessory);
		Assertions.assertEquals(template.getAccessoryType(),
			accessory.getAccessoryType());

	}

	@Test
	void testRollArmor() {}

	@Test
	void testRollComponent() {}

	@Test
	void testRollWeapon() {}

}
