package com.ikalagaming.factory.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * Tests for tags.
 *
 * @author Ches Burks
 */
class TestTag {

    /** Test the creation of tags. */
    @Test
    void testTagCreation() {
        try {
            Tag parent = new Tag("parent");
            assertDoesNotThrow(() -> new Tag("child", parent));
            assertDoesNotThrow(() -> new Tag("noParent", null));
        } catch (Exception e) {
            fail("Failed to create tags", e);
        }
    }

    @Test
    void testContainsTag() {
        var firstName = "foo";
        var secondName = "bar";
        var thirdName = "baz";
        var first = new Tag(firstName);
        var second = new Tag(secondName, first);

        var list = List.of(first, second);

        assertTrue(Tag.containsTag(firstName, list));
        assertTrue(Tag.containsTag(secondName, list));
        assertFalse(Tag.containsTag(thirdName, list));
        assertFalse(Tag.containsTag(firstName, List.of()));
        assertFalse(Tag.containsTag(firstName, null));
    }

    @Test
    void testFindTag() {
        var parent = new Tag("parent");
        var child = new Tag("child", parent);
        var unrelated = new Tag("unrelated");

        var list = List.of(parent, child);

        assertEquals(Optional.of(parent), Tag.findTag(parent, list));
        assertEquals(Optional.of(child), Tag.findTag(child, list));
        assertTrue(Tag.findTag(unrelated, list).isEmpty());
        assertTrue(Tag.findTag(parent, List.of()).isEmpty());
        assertTrue(Tag.findTag(parent, null).isEmpty());
    }

    @Test
    void testIsContainedBy() {
        var parent = new Tag("parent");
        var child = new Tag("child", parent);
        var unrelated = new Tag("unrelated");

        var parentList = List.of(parent);
        var childList = List.of(child);
        var combinedList = List.of(parent, child, unrelated);

        assertTrue(parent.isContainedBy(childList));
        assertTrue(parent.isContainedBy(parentList));
        assertTrue(child.isContainedBy(childList));
        assertFalse(child.isContainedBy(parentList));
        assertTrue(parent.isContainedBy(combinedList));
        assertTrue(child.isContainedBy(combinedList));
        assertTrue(unrelated.isContainedBy(combinedList));
        assertFalse(unrelated.isContainedBy(parentList));
        assertFalse(unrelated.isContainedBy(childList));
        assertFalse(parent.isContainedBy(List.of()));
        assertFalse(parent.isContainedBy(null));
    }
}
