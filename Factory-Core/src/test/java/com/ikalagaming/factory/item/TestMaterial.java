package com.ikalagaming.factory.item;

import com.ikalagaming.factory.world.Material;
import com.ikalagaming.factory.world.Tag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for materials.
 *
 * @author Ches Burks
 */
class TestMaterial {

    /** Check if we only compare materials by name. */
    @Test
    void testComparison() {
        Material parent1 = new Material("parent1");
        Material parent2 = new Material("parent2");

        Material first = new Material("same_name", parent1);
        Material second = new Material("same_name", parent2);
        Material third = new Material("same_name");
        Material fourth = new Material("same_name", List.of(new Tag("test")));
        Material fifth = new Material("same_name", new ArrayList<>(), parent2);

        Assertions.assertAll(
                () -> Assertions.assertNotEquals(parent1, parent2),
                () -> Assertions.assertEquals(first, second),
                () -> Assertions.assertEquals(second, first),
                () -> Assertions.assertEquals(first, third),
                () -> Assertions.assertEquals(first, fourth),
                () -> Assertions.assertEquals(first, fifth));
    }

    /** Check for the runtime errors from material constructors. */
    @Test
    void testMaterialConstructorExceptions() {
        Material parent = new Material("parent");
        final String nullName = null;
        final List<Tag> nullList = null;
        final Material nullParent = null;
        final List<Tag> normalList = new ArrayList<>();

        Assertions.assertThrows(
                NullPointerException.class, () -> new Material(nullName, normalList, parent));
        Assertions.assertThrows(
                NullPointerException.class, () -> new Material("Sample", nullList, parent));
        Assertions.assertThrows(NullPointerException.class, () -> new Material("Sample", nullList));
        Assertions.assertThrows(
                NullPointerException.class, () -> new Material("Sample", nullParent));
        Assertions.assertThrows(NullPointerException.class, () -> new Material(nullName));
    }

    /** Test the creation of materials. */
    @Test
    void testMaterialCreation() {
        Assertions.assertDoesNotThrow(() -> new Material("simple"));

        Material parent = new Material("parent");
        Assertions.assertAll(
                () -> new Material("Sample1"),
                () -> new Material("Sample2", new ArrayList<>()),
                () -> new Material("Sample3", parent),
                () -> new Material("Sample4", List.of(new Tag("not_modifiable"))),
                () -> new Material("Sample5", new ArrayList<>(), null),
                () -> new Material("Sample6", new ArrayList<>(), parent));
    }
}
