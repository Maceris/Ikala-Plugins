package com.ikalagaming.inventory;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.testing.ItemGenerator;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the inventory class.
 * 
 * @author Ches Burks
 *
 */
class TestInventory {

	/**
	 * Set up before all the tests.
	 *
	 * @throws Exception If something goes wrong.
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		EventManager.getInstance();
		PluginManager.getInstance();
		new InventoryPlugin().onLoad();
	}

	/**
	 * Tear down after all the tests.
	 *
	 * @throws Exception If something goes wrong.
	 */
	@AfterAll
	static void tearDownAfterClass() throws Exception {
		PluginManager.destoryInstance();
		EventManager.destoryInstance();
	}

	/**
	 * Tests the constructor.
	 */
	@Test
	void testConstructor() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			@SuppressWarnings("unused")
			Inventory ignored = new Inventory(-1);
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			@SuppressWarnings("unused")
			Inventory ignored = new Inventory(0);
		});

		Inventory inventory = new Inventory(1);
		Assertions.assertNotNull(inventory);
	}

	/**
	 * Test adding to the inventory.
	 */
	@Test
	void testAddItem() {
		Inventory inventory = new Inventory(2);
		for (int i = 0; i < inventory.getSize(); ++i) {
			Assertions
				.assertTrue(inventory.addItem(ItemGenerator.getAccessory()));
		}
		Assertions.assertFalse(inventory.addItem(ItemGenerator.getAccessory()));

		inventory = new Inventory(2);

		Item stackable = ItemGenerator.getJunk();
		Assertions.assertTrue(inventory.addItem(stackable, 1));

		int maxJunkStack = InvUtil.maxStackSize(stackable);
		Assertions.assertTrue(inventory.addItem(stackable, maxJunkStack));

		Assertions.assertEquals(maxJunkStack, inventory.getItemCount(0));
		Assertions.assertEquals(1, inventory.getItemCount(1));

		Assertions.assertFalse(inventory.addItem(stackable, maxJunkStack));
		Assertions.assertEquals(maxJunkStack, inventory.getItemCount(0));
		Assertions.assertEquals(maxJunkStack, inventory.getItemCount(1));

		inventory = new Inventory(2);

		stackable = ItemGenerator.getConsumable();

		int maxConsumableStack = InvUtil.maxStackSize(stackable);
		Assertions
			.assertTrue(inventory.addItem(stackable, maxConsumableStack * 3));

		Assertions.assertEquals(maxConsumableStack, inventory.getItemCount(0));
		Assertions.assertEquals(0, inventory.getItemCount(1));

		Assertions.assertTrue(inventory.addItem(stackable, maxJunkStack));
		Assertions.assertEquals(maxConsumableStack, inventory.getItemCount(0));
		Assertions.assertEquals(maxJunkStack, inventory.getItemCount(1));

		inventory = new Inventory(2);

		Assertions.assertFalse(inventory.addItem(stackable, -1));
		Assertions.assertFalse(inventory.addItem(stackable, 0));

		Assertions.assertTrue(inventory.isEmpty(0));
		Assertions.assertTrue(inventory.isEmpty(1));
	}

	/**
	 * Test the stack comparisons.
	 */
	@Test
	void testAreSameType() {
		Inventory inventory = new Inventory(5);

		/*
		 * Edge cases, we do default to false but are really watching for
		 * exceptions here.
		 */
		Assertions.assertFalse(inventory.areSameType(-1, 0));
		Assertions.assertFalse(inventory.areSameType(-1, -1));
		Assertions.assertFalse(inventory.areSameType(-1, 5));
		Assertions.assertFalse(inventory.areSameType(0, -1));
		Assertions.assertFalse(inventory.areSameType(0, 5));
		Assertions.assertFalse(inventory.areSameType(5, 5));

		// not stackable items, since they are empty, therefore false.
		Assertions.assertFalse(inventory.areSameType(0, 0));
		Assertions.assertFalse(inventory.areSameType(0, 1));

		Equipment equipment1 = ItemGenerator.getArmor();

		inventory.setItem(0, equipment1);
		inventory.setItem(1, equipment1);
		// Not stackable
		Assertions.assertFalse(inventory.areSameType(0, 1));
		Item stackable1 = ItemGenerator.getQuest();
		// overwrite slot 1 to be tricky
		inventory.setItem(1, stackable1, 1);
		inventory.setItem(2, stackable1, 3);

		Assertions.assertTrue(inventory.areSameType(1, 2));
		Assertions.assertTrue(inventory.areSameType(2, 1));
		Assertions.assertTrue(inventory.areSameType(1, 1));
		Assertions.assertFalse(inventory.areSameType(0, 1));
		Assertions.assertFalse(inventory.areSameType(1, 0));
		Assertions.assertFalse(inventory.areSameType(0, 4));

		Item stackable2 = ItemGenerator.getQuest();
		inventory.setItem(3, stackable2, 3);
		Assertions.assertFalse(inventory.areSameType(1, 3));
	}

	/**
	 * Test whether we can fit items when checking if we can add a single one.
	 */
	@Test
	void testCanFitItem() {
		Inventory inventory = new Inventory(1);
		Item nonStackable = ItemGenerator.getWeapon();
		Assertions.assertTrue(inventory.canFitItem(nonStackable));
		inventory.addItem(nonStackable);
		Assertions.assertFalse(inventory.canFitItem(nonStackable));
		inventory.clearSlot(0);
		Assertions.assertTrue(inventory.canFitItem(nonStackable));

		inventory = new Inventory(3);
		inventory.addItem(nonStackable);
		Item stackable = ItemGenerator.getMaterial();
		Assertions.assertTrue(inventory.canFitItem(stackable));
		inventory.addItem(stackable);
		Assertions.assertTrue(inventory.canFitItem(stackable));
		Item stackable2 = ItemGenerator.getMaterial();
		inventory.addItem(stackable2);
		inventory.addItem(nonStackable);
		Assertions.assertTrue(inventory.canFitItem(stackable));
		Assertions.assertFalse(inventory.canFitItem(nonStackable));
		inventory.addItem(stackable, InvUtil.maxStackSize(stackable));
		Assertions.assertFalse(inventory.canFitItem(stackable));
		Assertions.assertTrue(inventory.canFitItem(stackable2));
	}

	/**
	 * Test whether we can fit items when checking if we can add several.
	 */
	@Test
	void testCanFitItemStack() {
		Inventory inventory = new Inventory(1);
		Item nonStackable = ItemGenerator.getWeapon();
		Assertions.assertFalse(inventory.canFitItem(nonStackable, -1));
		Assertions.assertFalse(inventory.canFitItem(nonStackable, 0));

		Assertions.assertTrue(inventory.canFitItem(nonStackable, 1));
		inventory.addItem(nonStackable);
		Assertions.assertFalse(inventory.canFitItem(nonStackable, 1));
		inventory.clearSlot(0);
		Assertions.assertTrue(inventory.canFitItem(nonStackable, 1));

		inventory = new Inventory(3);
		Item stackable = ItemGenerator.getMaterial();
		Assertions.assertTrue(inventory.canFitItem(stackable, 1));
		Assertions.assertFalse(inventory.canFitItem(stackable,
			InvUtil.maxStackSize(stackable) * 4));
		Assertions.assertTrue(
			inventory.canFitItem(stackable, InvUtil.maxStackSize(stackable)));
		// Fill stack
		inventory.addItem(stackable, InvUtil.maxStackSize(stackable));

		Item stackable2 = ItemGenerator.getMaterial();
		inventory.addItem(stackable2, InvUtil.maxStackSize(stackable2));
		// Exact fit
		Assertions.assertTrue(
			inventory.canFitItem(stackable, InvUtil.maxStackSize(stackable)));
		inventory.addItem(stackable);
		// barely can't fit
		Assertions.assertFalse(
			inventory.canFitItem(stackable, InvUtil.maxStackSize(stackable)));
		Assertions.assertTrue(inventory.canFitItem(stackable,
			InvUtil.maxStackSize(stackable) - 1));
	}

	/**
	 * Test clearing out inventory slots.
	 */
	@Test
	void testClearSlot() {
		Inventory inventory = new Inventory(2);

		// Try an already empty slot
		inventory.clearSlot(0);

		// Try some impossible ones
		inventory.clearSlot(-1);
		inventory.clearSlot(2);
		inventory.clearSlot(99);

		inventory.addItem(ItemGenerator.getJunk());
		inventory.addItem(ItemGenerator.getWeapon());
		Assertions.assertFalse(inventory.isEmpty(0));
		Assertions.assertFalse(inventory.isEmpty(1));

		inventory.clearSlot(0);
		Assertions.assertTrue(inventory.isEmpty(0));
		Assertions.assertFalse(inventory.isEmpty(1));

		inventory.clearSlot(1);
		Assertions.assertTrue(inventory.isEmpty(0));
		Assertions.assertTrue(inventory.isEmpty(1));
	}

	@Test
	void testCombineSlots() {}

	/**
	 * Tests fetching items from the inventory.
	 */
	@Test
	void testGetItem() {
		Inventory inventory = new Inventory(5);
		Equipment first = ItemGenerator.getArmor();
		Equipment second = ItemGenerator.getArmor();
		Equipment third = ItemGenerator.getWeapon();
		Item fourth = ItemGenerator.getQuest();
		Item fifth = ItemGenerator.getQuest();

		// Mix up methods of adding and order, to make test more thorough
		inventory.setItem(0, first);
		inventory.addItem(second);
		inventory.setItem(3, fourth, 2);
		inventory.setItem(2, third);
		inventory.addItem(fifth);

		Assertions.assertNotNull(inventory.getItem(0));
		Assertions.assertNotNull(inventory.getItem(1));
		Assertions.assertNotNull(inventory.getItem(2));
		Assertions.assertNotNull(inventory.getItem(3));
		Assertions.assertNotNull(inventory.getItem(4));

		Assertions.assertTrue(inventory.getItem(0).isPresent());
		Assertions.assertTrue(inventory.getItem(1).isPresent());
		Assertions.assertTrue(inventory.getItem(2).isPresent());
		Assertions.assertTrue(inventory.getItem(3).isPresent());
		Assertions.assertTrue(inventory.getItem(4).isPresent());

		Assertions.assertEquals(first, inventory.getItem(0).get());
		Assertions.assertEquals(second, inventory.getItem(1).get());
		Assertions.assertEquals(third, inventory.getItem(2).get());
		Assertions.assertEquals(fourth, inventory.getItem(3).get());
		Assertions.assertEquals(fifth, inventory.getItem(4).get());

		// Edge cases
		Assertions.assertNotNull(inventory.getItem(-1));
		Assertions.assertNotNull(inventory.getItem(5));
		Assertions.assertTrue(inventory.getItem(-1).isEmpty());
		Assertions.assertTrue(inventory.getItem(5).isEmpty());
	}

	@Test
	void testGetItemCount() {}

	/**
	 * Tests logic for checking empty slots.
	 */
	@Test
	void testHasEmptySlot() {
		Inventory inventory = new Inventory(2);
		// Add and remove some items and check it reports empty correctly
		Assertions.assertTrue(inventory.hasEmptySlot());
		inventory.addItem(ItemGenerator.getWeapon());
		Assertions.assertTrue(inventory.hasEmptySlot());
		inventory.addItem(ItemGenerator.getWeapon());
		Assertions.assertFalse(inventory.hasEmptySlot());
		inventory.clearSlot(0);
		Assertions.assertTrue(inventory.hasEmptySlot());
		inventory.addItem(ItemGenerator.getWeapon());
		Assertions.assertFalse(inventory.hasEmptySlot());
		inventory.clearSlot(1);
		Assertions.assertTrue(inventory.hasEmptySlot());
		// Fill it back up and delete invalid slots, leaving it still full
		inventory.addItem(ItemGenerator.getWeapon());
		inventory.clearSlot(-1);
		inventory.clearSlot(2);
		Assertions.assertFalse(inventory.hasEmptySlot());
	}

	/**
	 * Check if slots report empty correctly, using both isEmpty and hasItem.
	 */
	@Test
	void testIsEmpty() {
		Inventory inventory = new Inventory(2);
		// Actually empty slots
		Assertions.assertTrue(inventory.isEmpty(0));
		Assertions.assertFalse(inventory.hasItem(0));
		Assertions.assertTrue(inventory.isEmpty(1));
		Assertions.assertFalse(inventory.hasItem(1));

		// impossible slots
		Assertions.assertTrue(inventory.isEmpty(-1));
		Assertions.assertFalse(inventory.hasItem(-1));
		Assertions.assertTrue(inventory.isEmpty(2));
		Assertions.assertFalse(inventory.hasItem(2));
		Assertions.assertTrue(inventory.isEmpty(99));
		Assertions.assertFalse(inventory.hasItem(99));

		inventory.addItem(ItemGenerator.getJunk());
		Assertions.assertFalse(inventory.isEmpty(0));
		Assertions.assertTrue(inventory.hasItem(0));
		Assertions.assertTrue(inventory.isEmpty(1));
		Assertions.assertFalse(inventory.hasItem(1));

		inventory.addItem(ItemGenerator.getWeapon());
		Assertions.assertFalse(inventory.isEmpty(0));
		Assertions.assertTrue(inventory.hasItem(0));
		Assertions.assertFalse(inventory.isEmpty(1));
		Assertions.assertTrue(inventory.hasItem(1));

		inventory.clearSlot(0);
		Assertions.assertTrue(inventory.isEmpty(0));
		Assertions.assertFalse(inventory.hasItem(0));
		Assertions.assertFalse(inventory.isEmpty(1));
		Assertions.assertTrue(inventory.hasItem(1));
	}

	/**
	 * Test forcibly setting non-stackable items.
	 */
	@Test
	void testSetItemNonStackable() {
		Inventory inventory = new Inventory(5);
		Equipment first = ItemGenerator.getArmor();

		// Check edge cases for exceptions
		inventory.setItem(-1, first);
		inventory.setItem(5, first);

		// Boring cases
		inventory.setItem(0, first);
		Assertions.assertEquals(first, inventory.getItem(0).get());

		Equipment second = ItemGenerator.getWeapon();
		inventory.setItem(1, second);
		Assertions.assertEquals(first, inventory.getItem(0).get());
		Assertions.assertEquals(second, inventory.getItem(1).get());
	}

	/**
	 * Test forcibly setting stackable items.
	 */
	@Test
	void testSetItemStackable() {
		Inventory inventory = new Inventory(5);

		Item first = ItemGenerator.getQuest();

		// Check edge cases for exceptions
		inventory.setItem(-1, first, 1);
		inventory.setItem(5, first, 1);
		inventory.setItem(0, first, -1);
		Assertions.assertTrue(inventory.isEmpty(0));

		// Will work but won't actually store above max stack size
		inventory.setItem(0, first, InvUtil.maxStackSize(first) + 1);
		Assertions.assertEquals(first, inventory.getItem(0).get());
		Assertions.assertEquals(InvUtil.maxStackSize(first),
			inventory.getItemCount(0));

		Item second = ItemGenerator.getMaterial();
		inventory.setItem(1, second, 5);
		Assertions.assertEquals(second, inventory.getItem(1).get());
		Assertions.assertEquals(5, inventory.getItemCount(1));
	}

	@Test
	void testSplitStack() {}

	@Test
	void testSwapSlots() {}

	/**
	 * Test the get size method.
	 */
	@Test
	void testGetSize() {
		Inventory inventory = new Inventory(1);
		Assertions.assertEquals(1, inventory.getSize());

		inventory = new Inventory(999);
		Assertions.assertEquals(999, inventory.getSize());
	}

}
