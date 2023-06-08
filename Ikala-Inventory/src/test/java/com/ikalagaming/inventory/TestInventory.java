package com.ikalagaming.inventory;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.inventory.Inventory.EquipmentSlot;
import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.Equipment;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.item.enums.AccessoryType;
import com.ikalagaming.item.enums.ArmorType;
import com.ikalagaming.item.enums.WeaponType;
import com.ikalagaming.item.testing.ItemGenerator;
import com.ikalagaming.plugins.PluginManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for the inventory class.
 *
 * @author Ches Burks
 *
 */
@Slf4j
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
	 * Check the status of the whole inventory based on what we expect to be
	 * there.
	 *
	 * @param inventory The inventory to check.
	 * @param expectedItems The items we expect in each slot.
	 * @param expectedAmounts The amounts we expect in each slot.
	 */
	private void checkStacks(@NonNull Inventory inventory,
		@NonNull List<Item> expectedItems,
		@NonNull List<Integer> expectedAmounts) {
		// Might as well fail instead of throwing an exception
		Assertions.assertEquals(expectedItems.size(), expectedAmounts.size());
		Assertions.assertEquals(expectedItems.size(), inventory.getSize());

		for (int i = 0; i < expectedItems.size(); ++i) {
			Item item = expectedItems.get(i);
			int count = expectedAmounts.get(i);
			if (item == null) {
				Assertions.assertTrue(inventory.isEmpty(i));
				Assertions.assertTrue(inventory.getItem(i).isEmpty());
				Assertions.assertEquals(0, inventory.getItemCount(i));
			}
			else {
				Assertions.assertFalse(inventory.isEmpty(i));
				Assertions.assertEquals(item, inventory.getItem(i).get());
				Assertions.assertEquals(count, inventory.getItemCount(i));
			}
		}
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

	/**
	 * Test combining slots.
	 */
	@Test
	void testCombineSlots() {
		Inventory inventory = new Inventory(6);
		Equipment first = ItemGenerator.getArmor();
		Item second = ItemGenerator.getQuest();
		Item third = ItemGenerator.getConsumable();

		inventory.setItem(0, first);
		inventory.setItem(1, second, 5);
		inventory.setItem(2, third, 11);
		inventory.setItem(5, second, 2);

		// Easy boundary tests
		Assertions.assertFalse(inventory.combineSlots(-1, 1));
		Assertions.assertFalse(inventory.combineSlots(-1, 6));
		Assertions.assertFalse(inventory.combineSlots(1, -1));
		Assertions.assertFalse(inventory.combineSlots(1, 6));
		Assertions.assertFalse(inventory.combineSlots(6, -1));
		Assertions.assertFalse(inventory.combineSlots(6, 1));

		// same slot
		Assertions.assertFalse(inventory.combineSlots(1, 1));
		Assertions.assertFalse(inventory.combineSlots(2, 2));

		// Unstackable
		Assertions.assertFalse(inventory.combineSlots(1, 2));
		Assertions.assertFalse(inventory.combineSlots(2, 1));

		// stackable
		Assertions.assertTrue(inventory.splitStack(1, 3));
		Assertions.assertTrue(inventory.splitStack(2, 9));

		Assertions.assertEquals(2, inventory.getItemCount(1));
		Assertions.assertEquals(2, inventory.getItemCount(2));
		Assertions.assertEquals(3, inventory.getItemCount(3));
		Assertions.assertEquals(9, inventory.getItemCount(4));
		Assertions.assertEquals(2, inventory.getItemCount(5));

		Assertions.assertEquals(second, inventory.getItem(1).get());
		Assertions.assertEquals(third, inventory.getItem(2).get());
		Assertions.assertEquals(second, inventory.getItem(3).get());
		Assertions.assertEquals(third, inventory.getItem(4).get());
		Assertions.assertEquals(second, inventory.getItem(5).get());

		Assertions.assertTrue(inventory.combineSlots(5, 3));
		Assertions.assertEquals(2, inventory.getItemCount(1));
		Assertions.assertEquals(2, inventory.getItemCount(2));
		Assertions.assertEquals(5, inventory.getItemCount(3));
		Assertions.assertEquals(9, inventory.getItemCount(4));
		Assertions.assertEquals(0, inventory.getItemCount(5));
		Assertions.assertTrue(inventory.isEmpty(5));

		Assertions.assertEquals(second, inventory.getItem(1).get());
		Assertions.assertEquals(third, inventory.getItem(2).get());
		Assertions.assertEquals(second, inventory.getItem(3).get());
		Assertions.assertEquals(third, inventory.getItem(4).get());
		Assertions.assertTrue(inventory.getItem(5).isEmpty());

		Assertions.assertTrue(inventory.combineSlots(3, 1));
		Assertions.assertEquals(7, inventory.getItemCount(1));
		Assertions.assertEquals(2, inventory.getItemCount(2));
		Assertions.assertEquals(0, inventory.getItemCount(3));
		Assertions.assertEquals(9, inventory.getItemCount(4));
		Assertions.assertEquals(0, inventory.getItemCount(5));
		Assertions.assertTrue(inventory.isEmpty(3));
		Assertions.assertTrue(inventory.isEmpty(5));

		Assertions.assertEquals(second, inventory.getItem(1).get());
		Assertions.assertEquals(third, inventory.getItem(2).get());
		Assertions.assertTrue(inventory.getItem(3).isEmpty());
		Assertions.assertEquals(third, inventory.getItem(4).get());
		Assertions.assertTrue(inventory.getItem(5).isEmpty());

		Assertions.assertTrue(inventory.combineSlots(4, 2));
		Assertions.assertEquals(7, inventory.getItemCount(1));
		Assertions.assertEquals(11, inventory.getItemCount(2));
		Assertions.assertEquals(0, inventory.getItemCount(3));
		Assertions.assertEquals(0, inventory.getItemCount(4));
		Assertions.assertEquals(0, inventory.getItemCount(5));
		Assertions.assertTrue(inventory.isEmpty(3));
		Assertions.assertTrue(inventory.isEmpty(4));
		Assertions.assertTrue(inventory.isEmpty(5));

		Assertions.assertEquals(second, inventory.getItem(1).get());
		Assertions.assertEquals(third, inventory.getItem(2).get());
		Assertions.assertTrue(inventory.getItem(3).isEmpty());
		Assertions.assertTrue(inventory.getItem(4).isEmpty());
		Assertions.assertTrue(inventory.getItem(5).isEmpty());

		Assertions.assertTrue(inventory.combineSlots(1, 4));
		Assertions.assertTrue(inventory.combineSlots(2, 5));
		Assertions.assertEquals(0, inventory.getItemCount(1));
		Assertions.assertEquals(0, inventory.getItemCount(2));
		Assertions.assertEquals(0, inventory.getItemCount(3));
		Assertions.assertEquals(7, inventory.getItemCount(4));
		Assertions.assertEquals(11, inventory.getItemCount(5));
		Assertions.assertTrue(inventory.isEmpty(1));
		Assertions.assertTrue(inventory.isEmpty(2));
		Assertions.assertTrue(inventory.isEmpty(3));

		Assertions.assertEquals(second, inventory.getItem(4).get());
		Assertions.assertEquals(third, inventory.getItem(5).get());
		Assertions.assertTrue(inventory.getItem(1).isEmpty());
		Assertions.assertTrue(inventory.getItem(2).isEmpty());
		Assertions.assertTrue(inventory.getItem(3).isEmpty());
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
	 * Test adding and removing equipment.
	 */
	@Test
	void testEquipment() {
		Map<Equipment, Inventory.EquipmentSlot> equipment = new HashMap<>();

		Accessory amulet = ItemGenerator.getAccessory();
		amulet.setAccessoryType(AccessoryType.AMULET);
		equipment.put(amulet, EquipmentSlot.AMULET);

		Accessory belt = ItemGenerator.getAccessory();
		belt.setAccessoryType(AccessoryType.BELT);
		equipment.put(belt, EquipmentSlot.BELT);

		Accessory cape = ItemGenerator.getAccessory();
		cape.setAccessoryType(AccessoryType.CAPE);
		equipment.put(cape, EquipmentSlot.CAPE);

		Accessory ring1 = ItemGenerator.getAccessory();
		ring1.setAccessoryType(AccessoryType.RING);
		equipment.put(ring1, EquipmentSlot.RING_RIGHT);

		Accessory ring2 = ItemGenerator.getAccessory();
		ring2.setAccessoryType(AccessoryType.RING);
		equipment.put(ring2, EquipmentSlot.RING_RIGHT);

		Accessory trinket = ItemGenerator.getAccessory();
		trinket.setAccessoryType(AccessoryType.TRINKET);
		equipment.put(trinket, EquipmentSlot.TRINKET);

		Armor chest = ItemGenerator.getArmor();
		chest.setArmorType(ArmorType.CHEST);
		equipment.put(chest, EquipmentSlot.CHEST);

		Armor feet = ItemGenerator.getArmor();
		feet.setArmorType(ArmorType.FEET);
		equipment.put(feet, EquipmentSlot.FEET);

		Armor hands = ItemGenerator.getArmor();
		hands.setArmorType(ArmorType.HANDS);
		equipment.put(hands, EquipmentSlot.HANDS);

		Armor head = ItemGenerator.getArmor();
		head.setArmorType(ArmorType.HEAD);
		equipment.put(head, EquipmentSlot.HEAD);

		Armor legs = ItemGenerator.getArmor();
		legs.setArmorType(ArmorType.LEGS);
		equipment.put(legs, EquipmentSlot.LEGS);

		Armor shoulders = ItemGenerator.getArmor();
		shoulders.setArmorType(ArmorType.SHOULDERS);
		equipment.put(shoulders, EquipmentSlot.SHOULDERS);

		Armor wrist = ItemGenerator.getArmor();
		wrist.setArmorType(ArmorType.WRIST);
		equipment.put(wrist, EquipmentSlot.WRIST);

		Weapon mainHand = ItemGenerator.getWeapon();
		mainHand.setWeaponType(WeaponType.ONE_HANDED_MELEE);
		equipment.put(mainHand, EquipmentSlot.MAIN_HAND);

		Weapon offHand = ItemGenerator.getWeapon();
		offHand.setWeaponType(WeaponType.SHIELD);
		equipment.put(offHand, EquipmentSlot.OFF_HAND);

		boolean placedRing = false;
		Inventory inventory = new Inventory(1, true);

		for (var entry : equipment.entrySet()) {
			EquipmentSlot slot = entry.getValue();
			if (slot.equals(EquipmentSlot.RING_RIGHT)) {
				// HashMap randomizes items, we don't know which is first
				if (placedRing) {
					slot = EquipmentSlot.RING_LEFT;
				}
				placedRing = true;
			}
			TestInventory.log.info("Adding item to slot {}", slot);
			Assertions.assertTrue(inventory.isEmpty(slot),
				"Slot should start empty for " + slot);
			Assertions.assertFalse(inventory.hasItem(slot),
				"Slot should start without item for " + slot);
			Assertions.assertTrue(inventory.canEquip(entry.getKey()),
				"We should be able to equip " + slot);
			Assertions.assertTrue(inventory.equip(entry.getKey()),
				"We should successfully equip " + slot);

			Assertions.assertFalse(inventory.isEmpty(slot),
				"Slot should not be empty after equiping " + slot);
			Assertions.assertTrue(inventory.hasItem(slot),
				"Slot should not have item after equiping " + slot);
		}

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			Assertions.assertTrue(inventory.hasItem(slot),
				"Inventory should have an item in the slot " + slot);
		}

		for (var entry : equipment.entrySet()) {
			Assertions.assertFalse(inventory.canEquip(entry.getKey()),
				"Full equipment inventory should not be able to equip "
					+ entry.getValue());
		}

		placedRing = false;
		for (var entry : equipment.entrySet()) {
			EquipmentSlot slot = entry.getValue();
			if (slot.equals(EquipmentSlot.RING_RIGHT)) {
				// HashMap randomizes items, we don't know which is first
				if (placedRing) {
					slot = EquipmentSlot.RING_LEFT;
				}
				placedRing = true;
			}
			inventory.clearSlot(slot);
			Assertions.assertTrue(inventory.isEmpty(slot),
				"cleared slot should be empty for " + slot);
			Assertions.assertFalse(inventory.hasItem(slot),
				"cleared slot should not have an item for " + slot);
			Assertions.assertTrue(inventory.canEquip(entry.getKey()),
				"cleared slot should be free to eqiup a " + slot);
		}

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			Assertions.assertFalse(inventory.hasItem(slot),
				"Inventory should not have an item in the slot " + slot);
		}
	}

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

	/**
	 * Check the item counts.
	 */
	@Test
	void testGetItemCount() {
		Inventory inventory = new Inventory(5);

		// Edge cases
		Assertions.assertEquals(0, inventory.getItemCount(-1));
		Assertions.assertEquals(0, inventory.getItemCount(5));

		// Normal stacks
		Item stackable = ItemGenerator.getQuest();
		inventory.setItem(0, stackable, InvUtil.maxStackSize(stackable));
		Assertions.assertEquals(InvUtil.maxStackSize(stackable),
			inventory.getItemCount(0));
		inventory.setItem(1, stackable, 1);
		Assertions.assertEquals(1, inventory.getItemCount(1));
		inventory.setItem(2, stackable, 5);
		Assertions.assertEquals(5, inventory.getItemCount(2));

		Equipment unstackable = ItemGenerator.getWeapon();
		inventory.setItem(3, unstackable);
		Assertions.assertEquals(1, inventory.getItemCount(3));

		// Try force overwriting
		inventory.setItem(1, unstackable);
		Assertions.assertEquals(1, inventory.getItemCount(1));

		inventory.setItem(3, stackable, 4);
		Assertions.assertEquals(4, inventory.getItemCount(3));
	}

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

	/**
	 * Test splitting stacks.
	 */
	@Test
	void testSplitStack() {
		Inventory inventory = new Inventory(5);
		Equipment first = ItemGenerator.getArmor();
		Item second = ItemGenerator.getQuest();
		Item third = ItemGenerator.getConsumable();

		inventory.setItem(0, first);
		inventory.setItem(1, second, 5);
		inventory.setItem(2, third, 11);

		// Easy boundary tests
		Assertions.assertFalse(inventory.splitStack(-1, 1));
		Assertions.assertFalse(inventory.splitStack(6, 1));
		Assertions.assertFalse(inventory.splitStack(2, -1));
		Assertions.assertFalse(inventory.splitStack(2, 0));

		// Unstackable
		Assertions.assertFalse(inventory.splitStack(0, 1));
		Assertions.assertFalse(inventory.splitStack(0, 2));

		// stackable
		Assertions.assertTrue(inventory.splitStack(1, 3));
		Assertions.assertEquals(2, inventory.getItemCount(1));
		Assertions.assertEquals(3, inventory.getItemCount(3));
		Assertions.assertEquals(second, inventory.getItem(1).get());
		Assertions.assertEquals(second, inventory.getItem(3).get());

		Assertions.assertTrue(inventory.splitStack(2, 9));
		Assertions.assertEquals(2, inventory.getItemCount(2));
		Assertions.assertEquals(9, inventory.getItemCount(4));
		Assertions.assertEquals(third, inventory.getItem(2).get());
		Assertions.assertEquals(third, inventory.getItem(4).get());

		// Full inventory
		Assertions.assertFalse(inventory.splitStack(2, 1));
		Assertions.assertFalse(inventory.splitStack(3, 1));
		Assertions.assertFalse(inventory.splitStack(4, 1));

	}

	/**
	 * Test swapping items around.
	 */
	@Test
	void testSwapSlots() {
		Inventory inventory = new Inventory(6);
		Equipment first = ItemGenerator.getArmor();
		Equipment second = ItemGenerator.getArmor();
		Item third = ItemGenerator.getQuest();
		Item fourth = ItemGenerator.getConsumable();

		List<Item> expectedItems =
			new ArrayList<>(List.of(first, second, third, fourth));
		expectedItems.add(null);
		expectedItems.add(null);
		List<Integer> expectedCounts =
			new ArrayList<>(List.of(1, 1, 5, 11, 0, 0));
		inventory.setItem(0, first);
		inventory.setItem(1, second);
		inventory.setItem(2, third, 5);
		inventory.setItem(3, fourth, 11);

		// Sanity check our starting state, before the wild swaps start
		Assertions.assertNotNull(inventory.getItem(0));
		Assertions.assertNotNull(inventory.getItem(1));
		Assertions.assertNotNull(inventory.getItem(2));
		Assertions.assertNotNull(inventory.getItem(3));
		Assertions.assertNotNull(inventory.getItem(4));
		Assertions.assertNotNull(inventory.getItem(5));

		Assertions.assertEquals(first, inventory.getItem(0).get());
		Assertions.assertEquals(second, inventory.getItem(1).get());
		Assertions.assertEquals(third, inventory.getItem(2).get());
		Assertions.assertEquals(fourth, inventory.getItem(3).get());
		Assertions.assertTrue(inventory.getItem(4).isEmpty());
		Assertions.assertTrue(inventory.getItem(5).isEmpty());

		// Swap two stackables
		inventory.swapSlots(0, 1);
		// now 2s, 1s, 3u, 4u, _, _
		Collections.swap(expectedItems, 0, 1);
		Collections.swap(expectedCounts, 0, 1);
		this.checkStacks(inventory, expectedItems, expectedCounts);

		// Swap unstackables
		inventory.swapSlots(3, 2);
		// now 2s, 1s, 4u, 3u, _, _
		Collections.swap(expectedItems, 3, 2);
		Collections.swap(expectedCounts, 3, 2);
		this.checkStacks(inventory, expectedItems, expectedCounts);

		// Swap stackable and empty slot
		inventory.swapSlots(1, 4);
		// now 2s, _, 4u, 3u, 1s, _
		Collections.swap(expectedItems, 1, 4);
		Collections.swap(expectedCounts, 1, 4);
		this.checkStacks(inventory, expectedItems, expectedCounts);

		// Swap stackable and unstackable
		inventory.swapSlots(0, 2);
		// now 4u, _, 2s, 3u, 1s, _
		Collections.swap(expectedItems, 0, 2);
		Collections.swap(expectedCounts, 0, 2);
		this.checkStacks(inventory, expectedItems, expectedCounts);

		// Swap empty slot and unstackable
		inventory.swapSlots(5, 3);
		// now 4u, _, 2s, _, 1s, 3u
		Collections.swap(expectedItems, 5, 3);
		Collections.swap(expectedCounts, 5, 3);
		this.checkStacks(inventory, expectedItems, expectedCounts);

		// Swap empty slots
		inventory.swapSlots(1, 3);
		// now 4u, _, 2s, _, 1s, 3u
		Collections.swap(expectedItems, 1, 3);
		Collections.swap(expectedCounts, 1, 3);
		this.checkStacks(inventory, expectedItems, expectedCounts);

		// invalid swaps
		inventory.swapSlots(0, 0);
		inventory.swapSlots(0, -1);
		inventory.swapSlots(2, 99);
		inventory.swapSlots(-1, 99);
		inventory.swapSlots(98, -1);
		inventory.swapSlots(-1, 4);
		inventory.swapSlots(99, 5);
		this.checkStacks(inventory, expectedItems, expectedCounts);
	}

}
