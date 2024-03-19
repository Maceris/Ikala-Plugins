package com.ikalagaming.factory.item;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.crafting.TransformItem;
import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Tests for items.
 *
 * @author Ches Burks
 */
class TestTransformItem {

    private Item expectedItem;
    private ItemStack stack;

    @BeforeEach
    void setUp() {
        var expectedName = "lotomation:sword";
        var expectedMaterial = "steel";
        var expectedTags = Set.of("weapon", "tool");
        KVT expectedKVT = new Node();
        expectedKVT.addInteger("damage", 100);
        expectedItem =
                Item.builder()
                        .name(expectedName)
                        .material(expectedMaterial)
                        .tags(expectedTags)
                        .kvt(expectedKVT)
                        .build();
        stack = new ItemStack(expectedItem);
    }

    @Test
    void testConsumeAll() {
        var result = TransformItem.CONSUME_ALL.transform(stack);

        assertNull(result);
    }

    @Test
    void testConsumeExact() {
        var startAmount = 10;
        stack = new ItemStack(expectedItem, startAmount);

        var result = TransformItem.consume(startAmount).transform(stack);

        assertNull(result);
    }

    @Test
    void testConsumeMoreThanMax() {
        var startAmount = 10;
        var toConsume = 50;
        stack = new ItemStack(expectedItem, startAmount);

        var result = TransformItem.consume(toConsume).transform(stack);

        assertNull(result);
    }

    @Test
    void testConsumeMultiple() {
        var startAmount = 10;
        var toConsume = 7;
        var expectedAmount = startAmount - toConsume;
        stack = new ItemStack(expectedItem, startAmount);

        var result = TransformItem.consume(toConsume).transform(stack);

        assertEquals(stack.withCount(expectedAmount), result);
    }

    @Test
    void testConsumeNegative() {
        assertThrows(IllegalArgumentException.class, () -> TransformItem.consume(-1));
    }

    @Test
    void testConsumeZero() {
        var result = TransformItem.consume(0).transform(stack);

        assertEquals(stack, result);
    }

    @Test
    void testReplace() {
        var expectedItem = new Item("lotomation:dirt");
        var expectedResult = new ItemStack(expectedItem);

        var result = TransformItem.replace(expectedResult).transform(stack);

        assertEquals(expectedResult, result);
    }

    @Test
    void testReuse() {
        var result = TransformItem.REUSE.transform(stack);

        assertEquals(stack, result);
    }
}
