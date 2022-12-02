package com.ikalagaming.inventory;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.item.Accessory;
import com.ikalagaming.item.Armor;
import com.ikalagaming.item.Consumable;
import com.ikalagaming.item.Item;
import com.ikalagaming.item.Junk;
import com.ikalagaming.item.Material;
import com.ikalagaming.item.Weapon;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * Tests for Item Stacks.
 *
 * @author Ches Burks
 *
 */
class TestInventorySlot {

	/**
	 * Set up before all the tests.
	 *
	 * @throws Exception If something goes wrong.
	 */
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		EventManager.getInstance();
		PluginManager.getInstance();
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
	 * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)}
	 * method.
	 */
	@Test
	void testCombine() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 1);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		Item secondItem = new Junk();
		secondItem.setID("Item ID");
		ItemStack secondStack = new ItemStack(secondItem, 2);
		InventorySlot secondSlot = new InventorySlot();
		secondSlot.setItemStack(secondStack);

		Accessory differentItem = new Accessory();
		differentItem.setID("Not Stackable");
		InventorySlot differentSlot = new InventorySlot();
		differentSlot.setItem(differentItem);

		// same object
		Assertions.assertFalse(InventorySlot.combine(firstSlot, firstSlot));
		Assertions.assertEquals(1, firstStack.getCount());
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertTrue(firstSlot.getItemStack().isPresent());
		Assertions.assertTrue(firstSlot.getItemStack().get().getItem().getID()
			.equals(firstItem.getID()));

		// source empty
		InventorySlot thirdSlot = new InventorySlot();
		Assertions.assertTrue(InventorySlot.combine(thirdSlot, firstSlot));
		Assertions.assertEquals(1, firstStack.getCount());
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertTrue(firstSlot.getItemStack().isPresent());
		Assertions.assertTrue(firstSlot.getItemStack().get().getItem().getID()
			.equals(firstItem.getID()));

		// destination empty
		Assertions.assertTrue(InventorySlot.combine(firstSlot, thirdSlot));
		Assertions.assertEquals(1, thirdSlot.getCount());
		Assertions.assertFalse(thirdSlot.isEmpty());
		Assertions.assertTrue(thirdSlot.getItemStack().isPresent());
		Assertions.assertTrue(thirdSlot.getItemStack().get().getItem().getID()
			.equals(firstItem.getID()));
		Assertions.assertTrue(firstSlot.isEmpty());
		Assertions.assertEquals(0, firstSlot.getCount());

		// either one not stackable
		Assertions
			.assertFalse(InventorySlot.combine(secondSlot, differentSlot));
		Assertions.assertEquals(2, secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertTrue(secondSlot.getItemStack().isPresent());
		Assertions.assertTrue(secondSlot.getItemStack().get().getItem().getID()
			.equals(secondItem.getID()));
		Assertions.assertFalse(differentSlot.isEmpty());
		Assertions.assertTrue(differentSlot.getItem().isPresent());
		Assertions.assertTrue(differentItem.getID()
			.equals(differentSlot.getItem().get().getID()));

		// different item types
		Item fourthItem = new Material();
		fourthItem.setID("Another ID");
		ItemStack fourthStack = new ItemStack(fourthItem, 5);
		InventorySlot fourthSlot = new InventorySlot();
		fourthSlot.setItemStack(fourthStack);
		Assertions.assertFalse(InventorySlot.combine(secondSlot, fourthSlot));
		Assertions.assertEquals(2, secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertTrue(secondSlot.getItemStack().isPresent());
		Assertions.assertTrue(secondSlot.getItemStack().get().getItem().getID()
			.equals(secondItem.getID()));
		Assertions.assertEquals(5, fourthSlot.getCount());
		Assertions.assertFalse(fourthSlot.isEmpty());
		Assertions.assertTrue(fourthSlot.getItemStack().isPresent());
		Assertions.assertTrue(fourthSlot.getItemStack().get().getItem().getID()
			.equals(fourthItem.getID()));

		// destination full
		Item fifthItem = new Junk();
		fifthItem.setID("Item ID");
		ItemStack fifthStack =
			new ItemStack(fifthItem, InvUtil.maxStackSize(fifthItem));
		InventorySlot fifthSlot = new InventorySlot();
		fifthSlot.setItemStack(fifthStack);
		Assertions.assertFalse(InventorySlot.combine(secondSlot, fifthSlot));
		Assertions.assertEquals(2, secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertTrue(secondSlot.getItemStack().isPresent());
		Assertions.assertTrue(secondSlot.getItemStack().get().getItem().getID()
			.equals(secondItem.getID()));
		Assertions.assertEquals(InvUtil.maxStackSize(fifthItem),
			fifthSlot.getCount());
		Assertions.assertFalse(fifthSlot.isEmpty());
		Assertions.assertTrue(fifthSlot.getItemStack().isPresent());
		Assertions.assertTrue(fifthSlot.getItemStack().get().getItem().getID()
			.equals(fifthItem.getID()));

		// partial transfer
		secondStack.setCount(5);
		fifthStack.setCount(InvUtil.maxStackSize(fifthItem) - 2);
		Assertions.assertFalse(InventorySlot.combine(secondSlot, fifthSlot));
		Assertions.assertEquals(3, secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertTrue(secondSlot.getItemStack().isPresent());
		Assertions.assertTrue(secondSlot.getItemStack().get().getItem().getID()
			.equals(secondItem.getID()));
		Assertions.assertEquals(InvUtil.maxStackSize(fifthItem),
			fifthSlot.getCount());
		Assertions.assertFalse(fifthSlot.isEmpty());
		Assertions.assertTrue(fifthSlot.getItemStack().isPresent());
		Assertions.assertTrue(fifthSlot.getItemStack().get().getItem().getID()
			.equals(fifthItem.getID()));

		// complete transfer
		secondStack.setCount(4);
		fifthStack.setCount(2);
		Assertions.assertTrue(InventorySlot.combine(secondSlot, fifthSlot));
		Assertions.assertEquals(0, secondSlot.getCount());
		Assertions.assertTrue(secondSlot.isEmpty());
		Assertions.assertTrue(secondSlot.getItemStack().isEmpty());
		Assertions.assertEquals(6, fifthSlot.getCount());
		Assertions.assertFalse(fifthSlot.isEmpty());
		Assertions.assertTrue(fifthSlot.getItemStack().isPresent());
		Assertions.assertTrue(fifthSlot.getItemStack().get().getItem().getID()
			.equals(fifthItem.getID()));
	}

	/**
	 * Test the {@link InventorySlot#getCount()} method.
	 */
	@Test
	void testGetCount() {
		InventorySlot slot = new InventorySlot();

		Assertions.assertEquals(0, slot.getCount());

		ItemStack stack = new ItemStack(new Consumable(), 4);
		slot.setItemStack(stack);
		Assertions.assertEquals(stack.getCount(), slot.getCount());

		slot.clear();
		Assertions.assertEquals(0, slot.getCount());

		slot.setItem(new Weapon());
		Assertions.assertEquals(1, slot.getCount());

		slot.clear();
		Assertions.assertEquals(0, slot.getCount());
	}

	/**
	 * Test the {@link InventorySlot#getItem()} method.
	 */
	@Test
	void testGetItem() {
		InventorySlot slot = new InventorySlot();

		Optional<Item> maybeItem;

		maybeItem = slot.getItem();
		Assertions.assertNotNull(maybeItem);
		Assertions.assertTrue(maybeItem.isEmpty());

		Material material = new Material();
		material.setID("Material ID");
		ItemStack stack = new ItemStack(material, 4);
		slot.setItemStack(stack);
		maybeItem = slot.getItem();
		Assertions.assertNotNull(maybeItem);
		Assertions.assertTrue(maybeItem.isPresent());
		Assertions.assertEquals(material.getID(), maybeItem.get().getID());

		slot.clear();
		maybeItem = slot.getItem();
		Assertions.assertNotNull(maybeItem);
		Assertions.assertTrue(maybeItem.isEmpty());

		Armor armor = new Armor();
		armor.setID("Armor ID");
		slot.setItem(armor);
		maybeItem = slot.getItem();
		Assertions.assertNotNull(maybeItem);
		Assertions.assertTrue(maybeItem.isPresent());
		Assertions.assertEquals(armor.getID(), maybeItem.get().getID());

		slot.clear();
		maybeItem = slot.getItem();
		Assertions.assertNotNull(maybeItem);
		Assertions.assertTrue(maybeItem.isEmpty());
	}

	/**
	 * Test the {@link InventorySlot#getItemStack()} method.
	 */
	@Test
	void testGetItemStack() {
		InventorySlot slot = new InventorySlot();

		Optional<ItemStack> maybeItemStack;

		maybeItemStack = slot.getItemStack();
		Assertions.assertNotNull(maybeItemStack);
		Assertions.assertTrue(maybeItemStack.isEmpty());

		Material material = new Material();
		material.setID("Material ID");
		ItemStack stack = new ItemStack(material, 4);
		slot.setItemStack(stack);
		maybeItemStack = slot.getItemStack();
		Assertions.assertNotNull(maybeItemStack);
		Assertions.assertTrue(maybeItemStack.isPresent());
		ItemStack stored = maybeItemStack.get();
		Assertions.assertTrue(ItemStack.isSameType(stack, stored));
		Assertions.assertTrue(stack.equals(stored));

		slot.clear();
		maybeItemStack = slot.getItemStack();
		Assertions.assertNotNull(maybeItemStack);
		Assertions.assertTrue(maybeItemStack.isEmpty());

		Armor armor = new Armor();
		armor.setID("Armor ID");
		slot.setItem(armor);
		maybeItemStack = slot.getItemStack();
		Assertions.assertNotNull(maybeItemStack);
		Assertions.assertTrue(maybeItemStack.isEmpty());

		slot.clear();
		maybeItemStack = slot.getItemStack();
		Assertions.assertNotNull(maybeItemStack);
		Assertions.assertTrue(maybeItemStack.isEmpty());
	}

	/**
	 * Test the {@link InventorySlot#isEmpty()} method.
	 */
	@Test
	void testIsEmpty() {
		InventorySlot slot = new InventorySlot();

		Assertions.assertTrue(slot.isEmpty());

		slot.setItemStack(new ItemStack(new Consumable(), 4));
		Assertions.assertFalse(slot.isEmpty());

		slot.clear();
		Assertions.assertTrue(slot.isEmpty());

		slot.setItem(new Weapon());
		Assertions.assertFalse(slot.isEmpty());
	}

	/**
	 * Test the {@link InventorySlot#isStackable()} method.
	 */
	@Test
	void testIsStackable() {
		InventorySlot slot = new InventorySlot();

		Assertions.assertTrue(slot.setItem(new Weapon()));
		Assertions.assertFalse(slot.isStackable());

		slot.clear();

		Assertions
			.assertTrue(slot.setItemStack(new ItemStack(new Consumable(), 4)));
		Assertions.assertTrue(slot.isStackable());

		slot.clear();
		Assertions.assertFalse(slot.isStackable());
	}

	/**
	 * Test the {@link InventorySlot#swapContents(InventorySlot, InventorySlot)}
	 * method.
	 */
	@Test
	void testSwapContents() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 1);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		Accessory secondItem = new Accessory();
		secondItem.setID("Not Stackable");
		InventorySlot secondSlot = new InventorySlot();
		secondSlot.setItem(secondItem);

		InventorySlot.swapContents(firstSlot, secondSlot);
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertTrue(secondSlot.isStackable());
		Assertions.assertFalse(firstSlot.isStackable());
		Assertions.assertTrue(
			firstSlot.getItem().get().getID().equals(secondItem.getID()));
		Assertions.assertTrue(
			secondSlot.getItem().get().getID().equals(firstItem.getID()));
		Assertions.assertTrue(firstSlot.getItemStack().isEmpty());
		Assertions.assertTrue(secondSlot.getItemStack().isPresent());
		Assertions.assertTrue(secondSlot.getItemStack().get().getItem().getID()
			.equals(firstItem.getID()));

		// swap back to be less confusing
		InventorySlot.swapContents(firstSlot, secondSlot);

		InventorySlot emptySlot = new InventorySlot();

		InventorySlot.swapContents(firstSlot, emptySlot);
		Assertions.assertTrue(firstSlot.isEmpty());
		Assertions.assertFalse(emptySlot.isEmpty());
		Assertions.assertFalse(firstSlot.isStackable());
		Assertions.assertTrue(emptySlot.isStackable());
		Assertions.assertTrue(
			emptySlot.getItem().get().getID().equals(firstItem.getID()));
		Assertions.assertTrue(firstSlot.getItem().isEmpty());
		Assertions.assertTrue(emptySlot.getItemStack().isPresent());
		Assertions.assertTrue(emptySlot.getItemStack().get().getItem().getID()
			.equals(firstItem.getID()));
	}
}
