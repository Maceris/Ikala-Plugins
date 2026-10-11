package com.ikalagaming.factory.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests for the hashing that seeds world generation.
 *
 * @author Ches Burks
 */
class TestWorldgenHash {

    /** FNV-1a matches its published test vectors. */
    @Test
    void testFnvVectors() {
        assertEquals(0xcbf29ce484222325L, WorldgenHash.fnv1a64(""));
        assertEquals(0xaf63dc4c8601ec8cL, WorldgenHash.fnv1a64("a"));
        assertEquals(0x85944171f73967e8L, WorldgenHash.fnv1a64("foobar"));
    }

    /** SplitMix64 from a state of 0 gives its published first output. */
    @Test
    void testMixVector() {
        assertEquals(0xe220a8397b1dcdafL, WorldgenHash.mix64(0));
    }

    /** Seeds differ by salt and by world seed, and repeat exactly. */
    @Test
    void testSeeds() {
        final long seed = 20261010L;
        assertEquals(
                WorldgenHash.seed(seed, "lotomation:temperature"),
                WorldgenHash.seed(seed, "lotomation:temperature"));
        assertNotEquals(
                WorldgenHash.seed(seed, "lotomation:temperature"),
                WorldgenHash.seed(seed, "lotomation:precipitation"));
        assertNotEquals(
                WorldgenHash.seed(seed, "lotomation:temperature"),
                WorldgenHash.seed(seed + 1, "lotomation:temperature"));
    }

    /** Combining depends on order. */
    @Test
    void testCombineOrder() {
        assertNotEquals(WorldgenHash.combine(1, 2), WorldgenHash.combine(2, 1));
    }
}
