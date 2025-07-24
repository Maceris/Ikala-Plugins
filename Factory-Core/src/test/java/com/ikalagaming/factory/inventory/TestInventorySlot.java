package com.ikalagaming.factory.inventory;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.item.Item;
import com.ikalagaming.factory.item.ItemStack;
import com.ikalagaming.plugins.PluginManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for Item Stacks.
 *
 * @author Ches Burks
 */
class TestInventorySlot {

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        EventManager.getInstance();
        PluginManager.getInstance();
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        PluginManager.destroyInstance();
        EventManager.destroyInstance();
    }

    /**
     * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)} method for when we can
     * transfer a whole stack.
     */
    @Test
    void testCombineCompleteTransfer() {
        var firstItem = Item.builder().name("lotomation:dirt").build();
        ItemStack firstStack = new ItemStack(firstItem, 2);
        InventorySlot firstSlot = new InventorySlot();
        firstSlot.setItemStack(firstStack);

        ItemStack secondStack = new ItemStack(firstItem, ItemStack.MAX_STACK_SIZE);
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
        Assertions.assertTrue(Item.isSameType(secondSlot.getItem(), firstItem));
    }

    /**
     * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)} method for when we are
     * trying to combine different types of items.
     */
    @Test
    void testCombineDifferentTypes() {
        var firstItem = Item.builder().name("lotomation:dirt").build();
        ItemStack firstStack = new ItemStack(firstItem, 2);
        InventorySlot firstSlot = new InventorySlot();
        firstSlot.setItemStack(firstStack);

        var secondItem = Item.builder().name("lotomation:grass").build();
        ItemStack secondStack = new ItemStack(secondItem, 5);
        InventorySlot secondSlot = new InventorySlot();
        secondSlot.setItemStack(secondStack);
        Assertions.assertFalse(InventorySlot.combine(firstSlot, secondSlot));
        Assertions.assertEquals(2, firstSlot.getCount());
        Assertions.assertFalse(firstSlot.isEmpty());
        Assertions.assertNotNull(firstSlot.getItemStack());
        Assertions.assertTrue(Item.isSameType(firstSlot.getItem(), firstItem));
        Assertions.assertEquals(5, secondSlot.getCount());
        Assertions.assertFalse(secondSlot.isEmpty());
        Assertions.assertNotNull(secondSlot.getItemStack());
        Assertions.assertTrue(Item.isSameType(secondSlot.getItem(), secondItem));
    }

    /**
     * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)} method for when we have
     * an empty destination.
     */
    @Test
    void testCombineEmptyDestination() {
        var firstItem = Item.builder().name("lotomation:dirt").build();
        ItemStack firstStack = new ItemStack(firstItem, 1);
        InventorySlot firstSlot = new InventorySlot();
        firstSlot.setItemStack(firstStack);

        InventorySlot secondSlot = new InventorySlot();

        Assertions.assertTrue(InventorySlot.combine(firstSlot, secondSlot));
        Assertions.assertEquals(1, secondSlot.getCount());
        Assertions.assertFalse(secondSlot.isEmpty());
        Assertions.assertNotNull(secondSlot.getItemStack());
        Assertions.assertTrue(Item.isSameType(secondSlot.getItem(), firstItem));
        Assertions.assertTrue(firstSlot.isEmpty());
        Assertions.assertEquals(0, firstSlot.getCount());
    }

    /**
     * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)} method for when we have
     * an empty source slot.
     */
    @Test
    void testCombineEmptySource() {
        var firstItem = Item.builder().name("lotomation:dirt").build();

        ItemStack firstStack = new ItemStack(firstItem, 1);
        InventorySlot firstSlot = new InventorySlot();
        firstSlot.setItemStack(firstStack);

        InventorySlot secondSlot = new InventorySlot();
        Assertions.assertTrue(InventorySlot.combine(secondSlot, firstSlot));
        Assertions.assertEquals(1, firstStack.getCount());
        Assertions.assertFalse(firstSlot.isEmpty());
        Assertions.assertNotNull(firstSlot.getItemStack());
        Assertions.assertTrue(Item.isSameType(firstSlot.getItem(), firstItem));
    }

    /**
     * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)} method for when we have
     * a full destination.
     */
    @Test
    void testCombineFullDestination() {
        var item = Item.builder().name("lotomation:dirt").build();

        ItemStack firstStack = new ItemStack(item, 2);
        InventorySlot firstSlot = new InventorySlot();
        firstSlot.setItemStack(firstStack);

        var secondItem = Item.builder().name("lotomation:grass").build();
        ItemStack secondStack = new ItemStack(secondItem, ItemStack.MAX_STACK_SIZE);
        InventorySlot secondSlot = new InventorySlot();
        secondSlot.setItemStack(secondStack);

        Assertions.assertFalse(InventorySlot.combine(firstSlot, secondSlot));
        Assertions.assertEquals(2, firstSlot.getCount());
        Assertions.assertFalse(firstSlot.isEmpty());
        Assertions.assertNotNull(firstSlot.getItemStack());
        Assertions.assertTrue(Item.isSameType(firstSlot.getItem(), item));
        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, secondSlot.getCount());
        Assertions.assertFalse(secondSlot.isEmpty());
        Assertions.assertNotNull(secondSlot.getItemStack());
        Assertions.assertTrue(Item.isSameType(firstSlot.getItem(), item));
        Assertions.assertTrue(Item.isSameType(secondSlot.getItem(), secondItem));
    }

    /**
     * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)} method for when we can
     * only partially transfer items.
     */
    @Test
    void testCombinePartialTransfer() {
        var item = Item.builder().name("lotomation:dirt").build();
        ItemStack firstStack = new ItemStack(item, 2);
        InventorySlot firstSlot = new InventorySlot();
        firstSlot.setItemStack(firstStack);

        ItemStack secondStack = new ItemStack(item, ItemStack.MAX_STACK_SIZE);
        InventorySlot secondSlot = new InventorySlot();
        secondSlot.setItemStack(secondStack);

        firstStack.setCount(5);
        secondStack.setCount(ItemStack.MAX_STACK_SIZE - 2);
        Assertions.assertFalse(InventorySlot.combine(firstSlot, secondSlot));
        Assertions.assertEquals(3, firstSlot.getCount());
        Assertions.assertFalse(firstSlot.isEmpty());
        Assertions.assertNotNull(firstSlot.getItemStack());
        Assertions.assertEquals(firstSlot.getItemStack().getItem().getName(), item.getName());
        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, secondSlot.getCount());
        Assertions.assertFalse(secondSlot.isEmpty());
        Assertions.assertNotNull(secondSlot.getItemStack());
        Assertions.assertTrue(Item.isSameType(secondSlot.getItem(), item));
    }

    /**
     * Test the {@link InventorySlot#combine(InventorySlot, InventorySlot)} method for when we try
     * and transfer a stack to itself.
     */
    @Test
    void testCombineSameObject() {
        var firstItem = Item.builder().name("lotomation:dirt").build();

        ItemStack firstStack = new ItemStack(firstItem, 1);
        InventorySlot firstSlot = new InventorySlot();
        firstSlot.setItemStack(firstStack);

        Assertions.assertFalse(InventorySlot.combine(firstSlot, firstSlot));
        Assertions.assertEquals(1, firstStack.getCount());
        Assertions.assertFalse(firstSlot.isEmpty());
        Assertions.assertNotNull(firstSlot.getItemStack());
        Assertions.assertEquals(firstSlot.getItemStack().getItem().getName(), firstItem.getName());
    }

    /** Test the {@link InventorySlot#getCount()} method. */
    @Test
    void testGetCount() {
        InventorySlot slot = new InventorySlot();

        Assertions.assertEquals(0, slot.getCount());

        ItemStack stack = new ItemStack(Item.builder().name("lotomation:dirt").build(), 4);
        slot.setItemStack(stack);
        Assertions.assertEquals(stack.getCount(), slot.getCount());

        slot.clear();
        Assertions.assertEquals(0, slot.getCount());

        slot.setItemStack(new ItemStack(Item.builder().name("lotomation:grass").build(), 1));
        Assertions.assertEquals(1, slot.getCount());

        slot.clear();
        Assertions.assertEquals(0, slot.getCount());
    }

    /** Test the {@link InventorySlot#getItem()} method. */
    @Test
    void testGetItem() {
        InventorySlot slot = new InventorySlot();

        Item item;

        item = slot.getItem();
        Assertions.assertNull(item);

        var firstItem = Item.builder().name("lotomation:dirt").build();
        ItemStack stack = new ItemStack(firstItem, 4);
        slot.setItemStack(stack);
        item = slot.getItem();
        Assertions.assertNotNull(item);
        Assertions.assertEquals(firstItem.getName(), item.getName());

        slot.clear();
        item = slot.getItem();
        Assertions.assertNull(item);

        var secondItem = Item.builder().name("lotomation:grass").build();
        slot.setItemStack(new ItemStack(secondItem, 1));
        item = slot.getItem();
        Assertions.assertNotNull(item);
        Assertions.assertEquals(secondItem.getName(), item.getName());

        slot.clear();
        item = slot.getItem();
        Assertions.assertNull(item);
    }

    /** Test the {@link InventorySlot#getItemStack()} method. */
    @Test
    void testGetItemStack() {
        InventorySlot slot = new InventorySlot();

        ItemStack itemStack;

        itemStack = slot.getItemStack();
        Assertions.assertNull(itemStack);

        var firstItem = Item.builder().name("lotomation:dirt").build();
        ItemStack stack = new ItemStack(firstItem, 4);
        slot.setItemStack(stack);
        itemStack = slot.getItemStack();
        Assertions.assertNotNull(itemStack);
        Assertions.assertTrue(ItemStack.isSameType(stack, itemStack));
        Assertions.assertEquals(stack, itemStack);

        slot.clear();
        itemStack = slot.getItemStack();
        Assertions.assertNull(itemStack);
    }

    /** Test the {@link InventorySlot#isEmpty()} method. */
    @Test
    void testIsEmpty() {
        InventorySlot slot = new InventorySlot();

        Assertions.assertTrue(slot.isEmpty());

        slot.setItemStack(new ItemStack(Item.builder().name("lotomation:dirt").build(), 4));
        Assertions.assertFalse(slot.isEmpty());

        slot.clear();
        Assertions.assertTrue(slot.isEmpty());

        slot.setItemStack(new ItemStack(Item.builder().name("lotomation:grass").build(), 1));
        Assertions.assertFalse(slot.isEmpty());
    }

    /** Test the {@link InventorySlot#swapContents(InventorySlot, InventorySlot)} method. */
    @Test
    void testSwapContents() {
        var firstItem = Item.builder().name("lotomation:dirt").build();

        ItemStack firstStack = new ItemStack(firstItem, 1);
        InventorySlot firstSlot = new InventorySlot();
        firstSlot.setItemStack(firstStack);

        var secondItem = Item.builder().name("lotomation:grass").build();
        InventorySlot secondSlot = new InventorySlot();
        secondSlot.setItemStack(new ItemStack(secondItem, 1));

        InventorySlot.swapContents(firstSlot, secondSlot);
        Assertions.assertFalse(firstSlot.isEmpty());
        Assertions.assertFalse(secondSlot.isEmpty());
        Assertions.assertTrue(Item.isSameType(firstSlot.getItem(), secondItem));
        Assertions.assertTrue(Item.isSameType(secondSlot.getItem(), firstItem));
        Assertions.assertNotNull(firstSlot.getItemStack());
        Assertions.assertNotNull(secondSlot.getItemStack());

        // swap back to be less confusing
        InventorySlot.swapContents(firstSlot, secondSlot);

        InventorySlot emptySlot = new InventorySlot();

        InventorySlot.swapContents(firstSlot, emptySlot);
        Assertions.assertTrue(firstSlot.isEmpty());
        Assertions.assertFalse(emptySlot.isEmpty());
        Assertions.assertTrue(Item.isSameType(emptySlot.getItem(), firstItem));
        Assertions.assertNull(firstSlot.getItem());
        Assertions.assertNotNull(emptySlot.getItemStack());
    }
}
