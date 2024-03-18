package com.ikalagaming.factory.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        ItemStack same1 = new ItemStack(item1, 1);
        ItemStack same2 = new ItemStack(item1, 2);
        ItemStack different = new ItemStack(item2, 1);

        assertTrue(ItemStack.isSameType(same1, same2));
        assertTrue(ItemStack.isSameType(same2, same1));
        assertFalse(ItemStack.isSameType(same1, different));
        assertFalse(ItemStack.isSameType(same2, different));
    }
}
