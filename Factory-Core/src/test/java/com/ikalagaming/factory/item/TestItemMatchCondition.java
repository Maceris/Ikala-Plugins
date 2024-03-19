package com.ikalagaming.factory.item;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.crafting.InputItem;
import com.ikalagaming.factory.crafting.ItemMatchCondition;
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
class TestItemMatchCondition {

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
    void testExactMatch() {
        var recipeItem = new InputItem(stack);
        KVT differentKVT = new Node();
        differentKVT.addInteger("damage", 99);

        var differentItem = expectedItem.toBuilder().kvt(differentKVT).build();
        var differentStack = new ItemStack(differentItem);

        var matcher = ItemMatchCondition.EXACT;

        assertTrue(matcher.matches(recipeItem, stack));
        assertFalse(matcher.matches(recipeItem, differentStack));
    }

    @Test
    void testNamesOnlyMatch() {
        var recipeItem = new InputItem(stack);
        KVT differentKVT = new Node();
        differentKVT.addInteger("damage", 99);

        var differentItem =
                expectedItem.toBuilder().kvt(differentKVT).tags(Set.of("different")).build();
        var differentStack = new ItemStack(differentItem);

        var matcher = ItemMatchCondition.NAME_ONLY;

        assertTrue(matcher.matches(recipeItem, stack));
        assertTrue(matcher.matches(recipeItem, differentStack));
    }
}
