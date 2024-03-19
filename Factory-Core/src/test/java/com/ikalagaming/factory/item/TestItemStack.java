package com.ikalagaming.factory.item;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;

import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Tests for Item Stacks.
 *
 * @author Ches Burks
 */
class TestItemStack {

    @Test
    void testConstructors() {
        var item = Item.builder().name("lotomation:dirt").build();

        var fullStack = new ItemStack(item, 1);
        var simpleStack = new ItemStack(item);

        assertEquals(1, fullStack.getCount());
        assertEquals(1, simpleStack.getCount());
    }

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
    void testToString() {
        KVT kvt = new Node();
        kvt.addLong("charge", 1000);
        var complexItem =
                Item.builder()
                        .name("lotomation:battery")
                        .material("iron")
                        .tags(Set.of("chargeable", "electronic"))
                        .kvt(kvt)
                        .build();

        var simpleItem = Item.builder().name("lotomation:grass").build();

        var complexStack = new ItemStack(complexItem, 25);
        var simpleStack = new ItemStack(simpleItem, 2);
        var singleStack = new ItemStack(simpleItem);

        assertDoesNotThrow(complexStack::toString);
        assertDoesNotThrow(simpleStack::toString);
        assertDoesNotThrow(singleStack::toString);
    }
}
