package com.ikalagaming.factory.inventory;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.factory.item.Item;
import com.ikalagaming.factory.item.ItemStack;
import com.ikalagaming.plugins.PluginManager;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Tests for the inventory class.
 *
 * @author Ches Burks
 */
@Slf4j
class TestInventory {
    private Item getUniqueItem() {
        int id = Math.round(((float) Math.random()) * Integer.MAX_VALUE);
        return Item.builder().name("lotomation:spare_part_" + id).build();
    }

    /** Set up before all the tests. */
    @BeforeAll
    static void setUpBeforeClass() {
        EventManager.getInstance();
        PluginManager.getInstance();
        new FactoryPlugin().onLoad();
    }

    /** Tear down after all the tests. */
    @AfterAll
    static void tearDownAfterClass() {
        PluginManager.destroyInstance();
        EventManager.destroyInstance();
    }

    /**
     * Check the status of the whole inventory based on what we expect to be there.
     *
     * @param inventory The inventory to check.
     * @param expectedItems The items we expect in each slot.
     * @param expectedAmounts The amounts we expect in each slot.
     */
    private void checkStacks(
            @NonNull Inventory inventory,
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
            } else {
                Assertions.assertFalse(inventory.isEmpty(i));
                Assertions.assertEquals(item, inventory.getItem(i).get());
                Assertions.assertEquals(count, inventory.getItemCount(i));
            }
        }
    }

    /** Test adding to the inventory. */
    @Test
    void testAddItem() {
        Inventory inventory = new Inventory(2);
        for (int i = 0; i < inventory.getSize(); ++i) {
            Assertions.assertTrue(inventory.addItem(getUniqueItem()));
        }
        Assertions.assertFalse(inventory.addItem(getUniqueItem()));

        inventory = new Inventory(2);

        Item stackable = getUniqueItem();
        Assertions.assertTrue(inventory.addItem(stackable, 1));

        Assertions.assertTrue(inventory.addItem(stackable, ItemStack.MAX_STACK_SIZE));

        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, inventory.getItemCount(0));
        Assertions.assertEquals(1, inventory.getItemCount(1));

        Assertions.assertFalse(inventory.addItem(stackable, ItemStack.MAX_STACK_SIZE));
        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, inventory.getItemCount(0));
        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, inventory.getItemCount(1));

        inventory = new Inventory(2);

        stackable = getUniqueItem();

        Assertions.assertTrue(inventory.addItem(stackable, ItemStack.MAX_STACK_SIZE + 1));

        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, inventory.getItemCount(0));
        Assertions.assertEquals(0, inventory.getItemCount(1));

        Assertions.assertTrue(inventory.addItem(stackable, ItemStack.MAX_STACK_SIZE));
        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, inventory.getItemCount(0));
        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, inventory.getItemCount(1));

        inventory = new Inventory(2);

        Assertions.assertFalse(inventory.addItem(stackable, -1));
        Assertions.assertFalse(inventory.addItem(stackable, 0));

        Assertions.assertTrue(inventory.isEmpty(0));
        Assertions.assertTrue(inventory.isEmpty(1));
    }

    /** Test the stack comparisons. */
    @Test
    void testIsSameType() {
        Inventory inventory = new Inventory(5);

        /*
         * Edge cases, we do default to false but are really watching for
         * exceptions here.
         */
        Assertions.assertFalse(inventory.isSameType(-1, 0));
        Assertions.assertFalse(inventory.isSameType(-1, -1));
        Assertions.assertFalse(inventory.isSameType(-1, 5));
        Assertions.assertFalse(inventory.isSameType(0, -1));
        Assertions.assertFalse(inventory.isSameType(0, 5));
        Assertions.assertFalse(inventory.isSameType(5, 5));

        // Empty slots are equal
        Assertions.assertTrue(inventory.isSameType(0, 0));
        Assertions.assertTrue(inventory.isSameType(0, 1));

        Item item1 = getUniqueItem();
        Item item2 = getUniqueItem();

        inventory.setItem(0, item1, 1);
        inventory.setItem(1, item1, 1);
        // overwrite slot 1 to be tricky
        inventory.setItem(1, item2, 1);

        Assertions.assertFalse(inventory.isSameType(0, 1));
        inventory.setItem(2, item2, 3);

        Assertions.assertTrue(inventory.isSameType(1, 2));
        Assertions.assertTrue(inventory.isSameType(2, 1));
        Assertions.assertTrue(inventory.isSameType(1, 1));
        Assertions.assertFalse(inventory.isSameType(0, 1));
        Assertions.assertFalse(inventory.isSameType(1, 0));
        Assertions.assertFalse(inventory.isSameType(0, 4));

        Item stackable2 = getUniqueItem();
        inventory.setItem(3, stackable2, 3);
        Assertions.assertFalse(inventory.isSameType(1, 3));
    }

    /** Test whether we can fit items when checking if we can add a single one. */
    @Test
    void testCanFitItem() {
        Inventory inventory = new Inventory(1);
        Item item1 = getUniqueItem();
        Item item2 = getUniqueItem();
        Item item3 = getUniqueItem();

        Assertions.assertTrue(inventory.canFitItem(item1));
        inventory.addItem(item1);
        Assertions.assertFalse(inventory.canFitItem(item2));
        inventory.clearSlot(0);
        Assertions.assertTrue(inventory.canFitItem(item2));

        inventory = new Inventory(3);
        inventory.addItem(item1);
        Assertions.assertTrue(inventory.canFitItem(item2));
        inventory.addItem(item2);
        Assertions.assertTrue(inventory.canFitItem(item2));
        inventory.addItem(item3);
        inventory.addItem(item1);
        Assertions.assertTrue(inventory.canFitItem(item2));
        Assertions.assertTrue(inventory.canFitItem(item1));
        inventory.addItem(item2, ItemStack.MAX_STACK_SIZE - 1);
        Assertions.assertFalse(inventory.canFitItem(item2));
        Assertions.assertTrue(inventory.canFitItem(item3));
    }

    /** Test whether we can fit items when checking if we can add several. */
    @Test
    void testCanFitItemStack() {
        Inventory inventory = new Inventory(1);
        Item item1 = getUniqueItem();
        Assertions.assertFalse(inventory.canFitItem(item1, -1));
        Assertions.assertFalse(inventory.canFitItem(item1, 0));

        Assertions.assertTrue(inventory.canFitItem(item1, 1));
        inventory.addItem(item1, ItemStack.MAX_STACK_SIZE);
        Assertions.assertFalse(inventory.canFitItem(item1, 1));
        inventory.clearSlot(0);
        Assertions.assertTrue(inventory.canFitItem(item1, 1));

        inventory = new Inventory(3);
        Item stackable = getUniqueItem();
        Assertions.assertTrue(inventory.canFitItem(stackable, 1));
        Assertions.assertTrue(inventory.canFitItem(stackable, ItemStack.MAX_STACK_SIZE + 1));
        Assertions.assertTrue(inventory.canFitItem(stackable, ItemStack.MAX_STACK_SIZE));
        // Fill stack
        inventory.addItem(stackable, ItemStack.MAX_STACK_SIZE);

        Item stackable2 = getUniqueItem();
        inventory.addItem(stackable2, ItemStack.MAX_STACK_SIZE);
        // Exact fit
        Assertions.assertTrue(inventory.canFitItem(stackable, ItemStack.MAX_STACK_SIZE));
        inventory.addItem(stackable);
        // barely can't fit
        Assertions.assertFalse(inventory.canFitItem(stackable, ItemStack.MAX_STACK_SIZE + 1));
        Assertions.assertTrue(inventory.canFitItem(stackable, ItemStack.MAX_STACK_SIZE - 1));
    }

    /** Test clearing out inventory slots. */
    @Test
    void testClearSlot() {
        Inventory inventory = new Inventory(2);

        // Try an already empty slot
        inventory.clearSlot(0);

        // Try some impossible ones
        inventory.clearSlot(-1);
        inventory.clearSlot(2);
        inventory.clearSlot(99);

        inventory.addItem(getUniqueItem());
        inventory.addItem(getUniqueItem());
        Assertions.assertFalse(inventory.isEmpty(0));
        Assertions.assertFalse(inventory.isEmpty(1));

        inventory.clearSlot(0);
        Assertions.assertTrue(inventory.isEmpty(0));
        Assertions.assertFalse(inventory.isEmpty(1));

        inventory.clearSlot(1);
        Assertions.assertTrue(inventory.isEmpty(0));
        Assertions.assertTrue(inventory.isEmpty(1));
    }

    /** Test combining slots. */
    @Test
    void testCombineSlots() {
        Inventory inventory = new Inventory(6);
        Item first = getUniqueItem();
        Item second = getUniqueItem();
        Item third = getUniqueItem();

        inventory.setItem(0, first, 1);
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

    /** Tests the constructor. */
    @Test
    void testConstructor() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> {
                    @SuppressWarnings("unused")
                    Inventory ignored = new Inventory(-1);
                });
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> {
                    @SuppressWarnings("unused")
                    Inventory ignored = new Inventory(0);
                });

        Inventory inventory = new Inventory(1);
        Assertions.assertNotNull(inventory);
    }

    /** Tests fetching items from the inventory. */
    @Test
    void testGetItem() {
        Inventory inventory = new Inventory(5);
        Item first = getUniqueItem();
        Item second = getUniqueItem();
        Item third = getUniqueItem();
        Item fourth = getUniqueItem();
        Item fifth = getUniqueItem();

        // Mix up methods of adding and order, to make test more thorough
        inventory.setItem(0, first, 1);
        inventory.addItem(second);
        inventory.setItem(3, fourth, 2);
        inventory.setItem(2, third, 1);
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

    /** Check the item counts. */
    @Test
    void testGetItemCount() {
        Inventory inventory = new Inventory(5);

        // Edge cases
        Assertions.assertEquals(0, inventory.getItemCount(-1));
        Assertions.assertEquals(0, inventory.getItemCount(5));

        // Normal stacks
        Item stackable = getUniqueItem();
        inventory.setItem(0, stackable, ItemStack.MAX_STACK_SIZE);
        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, inventory.getItemCount(0));
        inventory.setItem(1, stackable, 1);
        Assertions.assertEquals(1, inventory.getItemCount(1));
        inventory.setItem(2, stackable, 5);
        Assertions.assertEquals(5, inventory.getItemCount(2));

        Item unstackable = getUniqueItem();
        inventory.setItem(3, unstackable, 1);
        Assertions.assertEquals(1, inventory.getItemCount(3));

        // Try force overwriting
        inventory.setItem(1, unstackable, 1);
        Assertions.assertEquals(1, inventory.getItemCount(1));

        inventory.setItem(3, stackable, 4);
        Assertions.assertEquals(4, inventory.getItemCount(3));
    }

    /** Test the get size method. */
    @Test
    void testGetSize() {
        Inventory inventory = new Inventory(1);
        Assertions.assertEquals(1, inventory.getSize());

        inventory = new Inventory(999);
        Assertions.assertEquals(999, inventory.getSize());
    }

    /** Tests logic for checking empty slots. */
    @Test
    void testHasEmptySlot() {
        Inventory inventory = new Inventory(2);
        // Add and remove some items and check it reports empty correctly
        Assertions.assertTrue(inventory.hasEmptySlot());
        inventory.addItem(getUniqueItem());
        Assertions.assertTrue(inventory.hasEmptySlot());
        inventory.addItem(getUniqueItem());
        Assertions.assertFalse(inventory.hasEmptySlot());
        inventory.clearSlot(0);
        Assertions.assertTrue(inventory.hasEmptySlot());
        inventory.addItem(getUniqueItem());
        Assertions.assertFalse(inventory.hasEmptySlot());
        inventory.clearSlot(1);
        Assertions.assertTrue(inventory.hasEmptySlot());
        // Fill it back up and delete invalid slots, leaving it still full
        inventory.addItem(getUniqueItem());
        inventory.clearSlot(-1);
        inventory.clearSlot(2);
        Assertions.assertFalse(inventory.hasEmptySlot());
    }

    /** Check if slots report empty correctly, using both isEmpty and hasItem. */
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

        inventory.addItem(getUniqueItem());
        Assertions.assertFalse(inventory.isEmpty(0));
        Assertions.assertTrue(inventory.hasItem(0));
        Assertions.assertTrue(inventory.isEmpty(1));
        Assertions.assertFalse(inventory.hasItem(1));

        inventory.addItem(getUniqueItem());
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

    /** Test forcibly setting items. */
    @Test
    void testSetItem() {
        Inventory inventory = new Inventory(5);

        Item first = getUniqueItem();

        // Check edge cases for exceptions
        inventory.setItem(-1, first, 1);
        inventory.setItem(5, first, 1);
        inventory.setItem(0, first, -1);
        Assertions.assertTrue(inventory.isEmpty(0));

        // Will work but won't actually store above max stack size
        inventory.setItem(0, first, ItemStack.MAX_STACK_SIZE + 1);
        Assertions.assertTrue(inventory.getItem(0).isPresent());
        Assertions.assertEquals(first, inventory.getItem(0).get());
        Assertions.assertEquals(ItemStack.MAX_STACK_SIZE, inventory.getItemCount(0));

        Item second = getUniqueItem();
        inventory.setItem(1, second, 5);
        Assertions.assertTrue(inventory.getItem(1).isPresent());
        Assertions.assertEquals(second, inventory.getItem(1).get());
        Assertions.assertEquals(5, inventory.getItemCount(1));
    }

    /** Test splitting stacks. */
    @Test
    void testSplitStack() {
        Inventory inventory = new Inventory(5);
        Item first = getUniqueItem();
        Item second = getUniqueItem();
        Item third = getUniqueItem();

        inventory.setItem(0, first, 1);
        inventory.setItem(1, second, 5);
        inventory.setItem(2, third, 11);

        // Easy boundary tests
        Assertions.assertFalse(inventory.splitStack(-1, 1));
        Assertions.assertFalse(inventory.splitStack(6, 1));
        Assertions.assertFalse(inventory.splitStack(2, -1));
        Assertions.assertFalse(inventory.splitStack(2, 0));

        Assertions.assertFalse(inventory.splitStack(0, 1));
        Assertions.assertFalse(inventory.splitStack(0, 2));

        Assertions.assertTrue(inventory.getItem(0).isPresent());
        Assertions.assertTrue(inventory.getItem(1).isPresent());
        Assertions.assertTrue(inventory.getItem(2).isPresent());
        Assertions.assertTrue(inventory.getItem(3).isEmpty());

        // stackable
        Assertions.assertTrue(inventory.splitStack(1, 3));
        Assertions.assertTrue(inventory.getItem(3).isPresent());
        Assertions.assertEquals(2, inventory.getItemCount(1));
        Assertions.assertEquals(3, inventory.getItemCount(3));
        Assertions.assertEquals(second, inventory.getItem(1).get());
        Assertions.assertEquals(second, inventory.getItem(3).get());

        Assertions.assertTrue(inventory.splitStack(2, 9));
        Assertions.assertTrue(inventory.getItem(4).isPresent());
        Assertions.assertEquals(2, inventory.getItemCount(2));
        Assertions.assertEquals(9, inventory.getItemCount(4));
        Assertions.assertEquals(third, inventory.getItem(2).get());
        Assertions.assertEquals(third, inventory.getItem(4).get());

        // Full inventory
        Assertions.assertFalse(inventory.splitStack(2, 1));
        Assertions.assertFalse(inventory.splitStack(3, 1));
        Assertions.assertFalse(inventory.splitStack(4, 1));
    }

    /** Test swapping items around. */
    @Test
    void testSwapSlots() {
        Inventory inventory = new Inventory(6);
        Item first = getUniqueItem();
        Item second = getUniqueItem();
        Item third = getUniqueItem();
        Item fourth = getUniqueItem();

        List<Item> expectedItems = new ArrayList<>(List.of(first, second, third, fourth));
        expectedItems.add(null);
        expectedItems.add(null);
        List<Integer> expectedCounts = new ArrayList<>(List.of(1, 1, 5, 11, 0, 0));
        inventory.setItem(0, first, 1);
        inventory.setItem(1, second, 1);
        inventory.setItem(2, third, 5);
        inventory.setItem(3, fourth, 11);

        // Sanity check our starting state, before the wild swaps start
        Assertions.assertNotNull(inventory.getItem(0));
        Assertions.assertNotNull(inventory.getItem(1));
        Assertions.assertNotNull(inventory.getItem(2));
        Assertions.assertNotNull(inventory.getItem(3));
        Assertions.assertNotNull(inventory.getItem(4));
        Assertions.assertNotNull(inventory.getItem(5));

        Assertions.assertTrue(inventory.getItem(0).isPresent());
        Assertions.assertTrue(inventory.getItem(1).isPresent());
        Assertions.assertTrue(inventory.getItem(2).isPresent());
        Assertions.assertTrue(inventory.getItem(3).isPresent());

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
        checkStacks(inventory, expectedItems, expectedCounts);

        // Swap unstackables
        inventory.swapSlots(3, 2);
        // now 2s, 1s, 4u, 3u, _, _
        Collections.swap(expectedItems, 3, 2);
        Collections.swap(expectedCounts, 3, 2);
        checkStacks(inventory, expectedItems, expectedCounts);

        // Swap stackable and empty slot
        inventory.swapSlots(1, 4);
        // now 2s, _, 4u, 3u, 1s, _
        Collections.swap(expectedItems, 1, 4);
        Collections.swap(expectedCounts, 1, 4);
        checkStacks(inventory, expectedItems, expectedCounts);

        // Swap stackable and unstackable
        inventory.swapSlots(0, 2);
        // now 4u, _, 2s, 3u, 1s, _
        Collections.swap(expectedItems, 0, 2);
        Collections.swap(expectedCounts, 0, 2);
        checkStacks(inventory, expectedItems, expectedCounts);

        // Swap empty slot and unstackable
        inventory.swapSlots(5, 3);
        // now 4u, _, 2s, _, 1s, 3u
        Collections.swap(expectedItems, 5, 3);
        Collections.swap(expectedCounts, 5, 3);
        checkStacks(inventory, expectedItems, expectedCounts);

        // Swap empty slots
        inventory.swapSlots(1, 3);
        // now 4u, _, 2s, _, 1s, 3u
        Collections.swap(expectedItems, 1, 3);
        Collections.swap(expectedCounts, 1, 3);
        checkStacks(inventory, expectedItems, expectedCounts);

        // invalid swaps
        inventory.swapSlots(0, 0);
        inventory.swapSlots(0, -1);
        inventory.swapSlots(2, 99);
        inventory.swapSlots(-1, 99);
        inventory.swapSlots(98, -1);
        inventory.swapSlots(-1, 4);
        inventory.swapSlots(99, 5);
        checkStacks(inventory, expectedItems, expectedCounts);
    }
}
