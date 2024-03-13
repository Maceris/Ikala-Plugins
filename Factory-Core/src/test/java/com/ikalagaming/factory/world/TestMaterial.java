package com.ikalagaming.factory.world;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Tests for tags.
 *
 * @author Ches Burks
 */
class TestMaterial {

    private Tag parentTag;
    private Tag childTag;

    @BeforeEach
    void setUp() {
        parentTag = new Tag("parent");
        childTag = new Tag("child", parentTag);
    }

    @Test
    void testConstructionWithModifiableList() {
        List<Tag> list = new ArrayList<>();
        list.add(childTag);
        var name = "Sample";

        var material = new Material(name, list);

        assertEquals(list, material.tags());
        assertEquals(1, material.tags().size());

        list.add(new Tag("unrelated"));
        assertEquals(2, material.tags().size());
    }

    @Test
    void testConstructionWithUnmodifiableList() {
        List<Tag> list = List.of(childTag);
        var name = "Sample";

        var material = new Material(name, list);

        assertEquals(list, material.tags());
        assertEquals(1, material.tags().size());
    }

    @Test
    void testDeduplication() {
        var material = new Material("Mat", List.of(parentTag, childTag), null);

        assertEquals(2, material.tags().size());
        material.deduplicateTags();
        assertEquals(1, material.tags().size());
    }

    @Test
    void testEquals() {
        var material1 = new Material("Mat1", List.of(parentTag, childTag));
        var material2 = new Material("Mat2", List.of(parentTag, childTag));
        var material3 = new Material("Mat1", List.of(parentTag));
        var material4 = new Material("Mat2", List.of(), material1);

        assertEquals(material1, material1);
        assertEquals(material1, material3);
        assertEquals(material3, material1);
        assertEquals(material2, material4);
        assertEquals(material4, material2);
        assertNotEquals(material1, material2);
        assertNotEquals(material2, material1);
        assertNotEquals(material1, material4);
        assertNotEquals(material4, material1);
        assertNotEquals(material3, material4);
        assertNotEquals(material4, material3);
    }
}
