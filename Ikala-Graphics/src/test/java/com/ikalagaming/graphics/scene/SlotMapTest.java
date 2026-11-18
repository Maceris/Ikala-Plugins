package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Slots, generations and reuse in {@link SlotMap}. */
class SlotMapTest {

    /** A freed slot's old handle goes stale, and the slot is taken again with a new generation. */
    @Test
    void freedSlotsGoStaleAndAreReused() {
        final SlotMap<String> map = new SlotMap<>();
        final int a = map.add("a");
        final int b = map.add("b");
        final int generation = map.generation(a);
        assertEquals("a", map.get(a, generation));
        assertEquals(2, map.size());

        assertEquals("a", map.free(a));
        assertNull(map.get(a, generation));
        assertFalse(map.isLive(a, generation));
        assertEquals(1, map.size());

        final int c = map.add("c");
        assertEquals(a, c);
        assertEquals(generation + 1, map.generation(c));
        assertNull(map.get(c, generation));
        assertEquals("c", map.get(c, map.generation(c)));
        assertEquals("b", map.get(b, map.generation(b)));
        assertEquals(2, map.slots());
    }

    /** The most recently freed slot is taken first. */
    @Test
    void lastFreedIsTakenFirst() {
        final SlotMap<String> map = new SlotMap<>();
        for (int i = 0; i < 4; ++i) {
            map.add("v" + i);
        }
        map.free(1);
        map.free(3);
        assertEquals(3, map.add("x"));
        assertEquals(1, map.add("y"));
        assertEquals(4, map.add("z"));
    }

    /** Handles out of range or for free slots name nothing; freeing twice is an error. */
    @Test
    void badHandles() {
        final SlotMap<String> map = new SlotMap<>();
        assertNull(map.get(-1, 0));
        assertNull(map.get(0, 0));
        final int a = map.add("a");
        assertTrue(map.isLive(a, 0));
        map.free(a);
        assertNull(map.get(a));
        assertThrows(IllegalStateException.class, () -> map.free(a));
        assertThrows(IllegalStateException.class, () -> map.set(a, "b"));
    }

    /** Generations grow with the table past its first size. */
    @Test
    void growsPastInitialCapacity() {
        final SlotMap<Integer> map = new SlotMap<>();
        for (int i = 0; i < 100; ++i) {
            assertEquals(i, map.add(i));
        }
        for (int i = 0; i < 100; ++i) {
            assertEquals(i, map.get(i, 0));
        }
    }
}
