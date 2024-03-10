package com.ikalagaming.rpg.inventory;

import com.ikalagaming.event.EventManager;
import com.ikalagaming.plugins.PluginManager;
import com.ikalagaming.rpg.item.Consumable;
import com.ikalagaming.rpg.item.Item;
import com.ikalagaming.rpg.item.enums.ItemType;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for Item Stacks.
 *
 * @author Ches Burks
 */
class TestItemStack {

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

    /** Test the type comparison. */
    @Test
    void testIsSameType() {
        Item item1 = new Consumable();
        item1.setID("Common ID");
        item1.setItemType(ItemType.CONSUMABLE);
        Item item2 = new Consumable();
        item2.setID("Common ID");
        item2.setItemType(ItemType.CONSUMABLE);
        Item different = new Consumable();
        different.setID("Different ID");
        different.setItemType(ItemType.CONSUMABLE);

        ItemStack stack1 = new ItemStack(item1, 1);
        ItemStack stack2 = new ItemStack(item2, 2);
        ItemStack differentStack = new ItemStack(different, 1);

        Assertions.assertTrue(ItemStack.isSameType(stack1, stack2));
        Assertions.assertTrue(ItemStack.isSameType(stack2, stack1));
        Assertions.assertTrue(ItemStack.isSameType(new ItemStack(), new ItemStack()));
        Assertions.assertFalse(ItemStack.isSameType(stack1, differentStack));
        Assertions.assertFalse(ItemStack.isSameType(differentStack, stack2));
        Assertions.assertFalse(ItemStack.isSameType(stack1, new ItemStack()));
        Assertions.assertFalse(ItemStack.isSameType(new ItemStack(), stack2));
    }
}
