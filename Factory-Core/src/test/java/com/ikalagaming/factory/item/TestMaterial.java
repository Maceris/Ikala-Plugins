package com.ikalagaming.factory.item;

import static org.junit.jupiter.api.Assertions.*;

import com.ikalagaming.factory.world.Material;
import com.ikalagaming.factory.world.Tag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

        assertAll(
                () -> assertNotEquals(parent1, parent2),
                () -> assertEquals(first, second),
                () -> assertEquals(second, first),
                () -> assertEquals(first, third),
                () -> assertEquals(first, fourth),
                () -> assertEquals(first, fifth));
    }

    /** Check for the runtime errors from material constructors. */
    @Test
    void testMaterialConstructorExceptions() {
        Material parent = new Material("parent");
        final String nullName = null;
        final List<Tag> nullList = null;
        final List<Tag> normalList = new ArrayList<>();

        assertThrows(NullPointerException.class, () -> new Material(nullName, normalList, parent));
        assertThrows(NullPointerException.class, () -> new Material("Sample", nullList, parent));
        assertThrows(NullPointerException.class, () -> new Material("Sample", nullList));
        assertThrows(NullPointerException.class, () -> new Material(nullName));
    }

    /** Test the creation of materials. */
    @Test
    void testMaterialCreation() {
        assertDoesNotThrow(() -> new Material("simple"));

        Material parent = new Material("parent");
        assertAll(
                () -> new Material("Sample1"),
                () -> new Material("Sample2", new ArrayList<>()),
                () -> new Material("Sample3", parent),
                () -> new Material("Sample4", List.of(new Tag("not_modifiable"))),
                () -> new Material("Sample5", new ArrayList<>(), null),
                () -> new Material("Sample6", new ArrayList<>(), parent));
    }
}
