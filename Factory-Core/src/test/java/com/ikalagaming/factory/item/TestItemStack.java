package com.ikalagaming.factory.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for Item Stacks.
 *
 * @author Ches Burks
 */
class TestItemStack {

    /** Test the type comparison. */
    @Test
    void testIsSameType() {
        var item1 = Item.builder().name("lotomation:dirt").build();
        var item2 = Item.builder().name("lotomation:grass").build();

        var same1 = new ItemStack(item1, 1);
        var same2 = new ItemStack(item1, 2);
        var different = new ItemStack(item2, 1);

        assertTrue(ItemStack.isSameType(same1, same2));
        assertTrue(ItemStack.isSameType(same2, same1));
        assertFalse(ItemStack.isSameType(same1, different));
        assertFalse(ItemStack.isSameType(same2, different));
    }

    @Test
    void testConstructors() {
        var item = Item.builder().name("lotomation:dirt").build();

        var fullStack = new ItemStack(item, 1);
        var simpleStack = new ItemStack(item);

        assertEquals(1, fullStack.getCount());
        assertEquals(1, simpleStack.getCount());
    }
}
