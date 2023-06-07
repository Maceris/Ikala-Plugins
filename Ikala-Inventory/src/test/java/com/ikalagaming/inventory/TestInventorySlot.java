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
	 * method for when we can transfer a whole stack.
	 */
	@Test
	void testCombineCompleteTransfer() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 2);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		Item secondItem = new Junk();
		secondItem.setID("Item ID");
		ItemStack secondStack =
			new ItemStack(secondItem, InvUtil.maxStackSize(secondItem));
		InventorySlot secondSlot = new InventorySlot();
		secondSlot.setItemStack(secondStack);

		firstStack.setCount(4);
		secondStack.setCount(2);
		Assertions.assertTrue(InventorySlot.combine(firstSlot, secondSlot));
		Assertions.assertEquals(0, firstSlot.getCount());
		Assertions.assertTrue(firstSlot.isEmpty());
		Assertions.assertNull(firstSlot.getItemStack());
		Assertions.assertEquals(6, secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertNotNull(secondSlot.getItemStack());
		Assertions.assertEquals(secondSlot.getItemStack().getItem().getID(),
			secondItem.getID());
	}

	/**
	 * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)}
	 * method for when we are trying to combine different types of items.
	 */
	@Test
	void testCombineDifferentTypes() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 2);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		Item secondItem = new Material();
		secondItem.setID("Another ID");
		ItemStack secondStack = new ItemStack(secondItem, 5);
		InventorySlot secondSlot = new InventorySlot();
		secondSlot.setItemStack(secondStack);
		Assertions.assertFalse(InventorySlot.combine(firstSlot, secondSlot));
		Assertions.assertEquals(2, firstSlot.getCount());
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertNotNull(firstSlot.getItemStack());
		Assertions.assertEquals(firstSlot.getItemStack().getItem().getID(),
			firstItem.getID());
		Assertions.assertEquals(5, secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertNotNull(secondSlot.getItemStack());
		Assertions.assertEquals(secondSlot.getItemStack().getItem().getID(),
			secondItem.getID());
	}

	/**
	 * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)}
	 * method for when we have an empty destination.
	 */
	@Test
	void testCombineEmptyDestination() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 1);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		InventorySlot secondSlot = new InventorySlot();

		Assertions.assertTrue(InventorySlot.combine(firstSlot, secondSlot));
		Assertions.assertEquals(1, secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertNotNull(secondSlot.getItemStack());
		Assertions.assertEquals(secondSlot.getItemStack().getItem().getID(),
			firstItem.getID());
		Assertions.assertTrue(firstSlot.isEmpty());
		Assertions.assertEquals(0, firstSlot.getCount());
	}

	/**
	 * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)}
	 * method for when we have an empty source slot.
	 */
	@Test
	void testCombineEmptySource() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 1);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		InventorySlot secondSlot = new InventorySlot();
		Assertions.assertTrue(InventorySlot.combine(secondSlot, firstSlot));
		Assertions.assertEquals(1, firstStack.getCount());
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertNotNull(firstSlot.getItemStack());
		Assertions.assertEquals(firstSlot.getItemStack().getItem().getID(),
			firstItem.getID());
	}

	/**
	 * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)}
	 * method for when we have a full destination.
	 */
	@Test
	void testCombineFullDestination() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 2);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		Item secondItem = new Junk();
		secondItem.setID("Item ID");
		ItemStack secondStack =
			new ItemStack(secondItem, InvUtil.maxStackSize(secondItem));
		InventorySlot secondSlot = new InventorySlot();
		secondSlot.setItemStack(secondStack);

		Assertions.assertFalse(InventorySlot.combine(firstSlot, secondSlot));
		Assertions.assertEquals(2, firstSlot.getCount());
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertNotNull(firstSlot.getItemStack());
		Assertions.assertEquals(firstSlot.getItemStack().getItem().getID(),
			firstItem.getID());
		Assertions.assertEquals(InvUtil.maxStackSize(secondItem),
			secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertNotNull(secondSlot.getItemStack());
		Assertions.assertEquals(secondSlot.getItemStack().getItem().getID(),
			secondItem.getID());
	}

	/**
	 * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)}
	 * method for non-stackable items.
	 */
	@Test
	void testCombineNonStackables() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 2);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		Accessory secondItem = new Accessory();
		secondItem.setID("Not Stackable");
		InventorySlot secondSlot = new InventorySlot();
		secondSlot.setItem(secondItem);

		Assertions.assertFalse(InventorySlot.combine(firstSlot, secondSlot));
		Assertions.assertEquals(2, firstSlot.getCount());
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertNotNull(firstSlot.getItemStack());
		Assertions.assertEquals(firstSlot.getItemStack().getItem().getID(),
			firstItem.getID());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertNotNull(secondSlot.getItem());
		Assertions.assertEquals(secondItem.getID(),
			secondSlot.getItem().getID());
	}

	/**
	 * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)}
	 * method for when we can only partially transfer items.
	 */
	@Test
	void testCombinePartialTransfer() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 2);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		Item secondItem = new Junk();
		secondItem.setID("Item ID");
		ItemStack secondStack =
			new ItemStack(secondItem, InvUtil.maxStackSize(secondItem));
		InventorySlot secondSlot = new InventorySlot();
		secondSlot.setItemStack(secondStack);

		firstStack.setCount(5);
		secondStack.setCount(InvUtil.maxStackSize(secondItem) - 2);
		Assertions.assertFalse(InventorySlot.combine(firstSlot, secondSlot));
		Assertions.assertEquals(3, firstSlot.getCount());
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertNotNull(firstSlot.getItemStack());
		Assertions.assertEquals(firstSlot.getItemStack().getItem().getID(),
			firstItem.getID());
		Assertions.assertEquals(InvUtil.maxStackSize(secondItem),
			secondSlot.getCount());
		Assertions.assertFalse(secondSlot.isEmpty());
		Assertions.assertNotNull(secondSlot.getItemStack());
		Assertions.assertEquals(secondSlot.getItemStack().getItem().getID(),
			secondItem.getID());
	}

	/**
	 * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)}
	 * method for when we try and transfer a stack to itself.
	 */
	@Test
	void testCombineSameObject() {
		Item firstItem = new Junk();
		firstItem.setID("Item ID");
		ItemStack firstStack = new ItemStack(firstItem, 1);
		InventorySlot firstSlot = new InventorySlot();
		firstSlot.setItemStack(firstStack);

		Assertions.assertFalse(InventorySlot.combine(firstSlot, firstSlot));
		Assertions.assertEquals(1, firstStack.getCount());
		Assertions.assertFalse(firstSlot.isEmpty());
		Assertions.assertNotNull(firstSlot.getItemStack());
		Assertions.assertEquals(firstSlot.getItemStack().getItem().getID(),
			firstItem.getID());
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

		Item item;

		item = slot.getItem();
		Assertions.assertNull(item);

		Material material = new Material();
		material.setID("Material ID");
		ItemStack stack = new ItemStack(material, 4);
		slot.setItemStack(stack);
		item = slot.getItem();
		Assertions.assertNotNull(item);
		Assertions.assertEquals(material.getID(), item.getID());

		slot.clear();
		item = slot.getItem();
		Assertions.assertNull(item);

		Armor armor = new Armor();
		armor.setID("Armor ID");
		slot.setItem(armor);
		item = slot.getItem();
		Assertions.assertNotNull(item);
		Assertions.assertEquals(armor.getID(), item.getID());

		slot.clear();
		item = slot.getItem();
		Assertions.assertNull(item);
	}

	/**
	 * Test the {@link InventorySlot#getItemStack()} method.
	 */
	@Test
	void testGetItemStack() {
		InventorySlot slot = new InventorySlot();

		ItemStack itemStack;

		itemStack = slot.getItemStack();
		Assertions.assertNull(itemStack);

		Material material = new Material();
		material.setID("Material ID");
		ItemStack stack = new ItemStack(material, 4);
		slot.setItemStack(stack);
		itemStack = slot.getItemStack();
		Assertions.assertNotNull(itemStack);
		Assertions.assertTrue(ItemStack.isSameType(stack, itemStack));
		Assertions.assertEquals(stack, itemStack);

		slot.clear();
		itemStack = slot.getItemStack();
		Assertions.assertNull(itemStack);

		Armor armor = new Armor();
		armor.setID("Armor ID");
		slot.setItem(armor);
		itemStack = slot.getItemStack();
		Assertions.assertNull(itemStack);

		slot.clear();
		itemStack = slot.getItemStack();
		Assertions.assertNull(itemStack);
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
		Assertions.assertEquals(firstSlot.getItem().getID(),
			secondItem.getID());
		Assertions.assertEquals(secondSlot.getItem().getID(),
			firstItem.getID());
		Assertions.assertNull(firstSlot.getItemStack());
		Assertions.assertNotNull(secondSlot.getItemStack());
		Assertions.assertEquals(secondSlot.getItemStack().getItem().getID(),
			firstItem.getID());

		// swap back to be less confusing
		InventorySlot.swapContents(firstSlot, secondSlot);

		InventorySlot emptySlot = new InventorySlot();

		InventorySlot.swapContents(firstSlot, emptySlot);
		Assertions.assertTrue(firstSlot.isEmpty());
		Assertions.assertFalse(emptySlot.isEmpty());
		Assertions.assertFalse(firstSlot.isStackable());
		Assertions.assertTrue(emptySlot.isStackable());
		Assertions.assertEquals(emptySlot.getItem().getID(), firstItem.getID());
		Assertions.assertNull(firstSlot.getItem());
		Assertions.assertNotNull(emptySlot.getItemStack());
		Assertions.assertEquals(emptySlot.getItemStack().getItem().getID(),
			firstItem.getID());
	}
}
