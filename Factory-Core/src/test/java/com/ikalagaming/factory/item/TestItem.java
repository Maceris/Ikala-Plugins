package com.ikalagaming.factory.item;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.kvt.KVT;
import com.ikalagaming.factory.kvt.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for items.
 *
 * @author Ches Burks
 */
class TestItem {

    private String expectedName;
    private String expectedMaterial;
    private List<String> expectedTags;
    private KVT expectedKVT;

    @BeforeEach
    void setUp() {
        expectedName = "lotomation:dirt";
        expectedMaterial = "dirt";
        expectedTags = List.of("dirty", "clownlike");
        expectedKVT = new Node();
        expectedKVT.addInteger("dirt_dirtiness", 100);
    }

    @Test
    void testItemBuilder() {
        var result =
                Item.builder()
                        .name(expectedName)
                        .material(expectedMaterial)
                        .tags(expectedTags)
                        .kvt(expectedKVT)
                        .build();

        assertNotNull(result);
        assertEquals(expectedName, result.getName());
        assertEquals(expectedMaterial, result.getMaterial());
        assertEquals(expectedTags, result.getTags());
        assertEquals(expectedKVT, result.getKvt());
    }

    @Test
    void testItemBuilderMissingFields() {
        var builder = Item.builder();

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void testItemBuilderOnlyName() {
        var result = Item.builder().name(expectedName).build();

        assertNotNull(result);
        assertEquals(expectedName, result.getName());
        assertNull(result.getMaterial());
        assertNotNull(result.getTags());
        assertTrue(result.getTags().isEmpty());
        assertNull(result.getKvt());
    }

    @Test
    void testToString() {
        var complex =
                Item.builder()
                        .name(expectedName)
                        .material(expectedMaterial)
                        .tags(expectedTags)
                        .kvt(expectedKVT)
                        .build();

        var simple = Item.builder().name(expectedName).build();

        assertDoesNotThrow(complex::toString);
        assertDoesNotThrow(simple::toString);
    }
}
