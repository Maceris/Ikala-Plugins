package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for the long to int map. */
class LongIntMapTest {

    /** Pairs whose halves XOR to the same value, which collide in a boxed Long set, all stay. */
    @Test
    void testCollidingPairsAllKept() {
        LongIntMap map = new LongIntMap(0);
        final int count = 5000;
        for (int a = 0; a < count; ++a) {
            // a ^ (a ^ 7) is always 7
            assertEquals(LongIntMap.MISSING, map.putIfAbsent(LongIntMap.pair(a, a ^ 7), a));
        }
        assertEquals(count, map.size());
        for (int a = 0; a < count; ++a) {
            assertEquals(a, map.get(LongIntMap.pair(a, a ^ 7)));
        }
    }

    /** A key already there keeps its value, and a missing key reads as missing. */
    @Test
    void testPutIfAbsentAndMissing() {
        LongIntMap map = new LongIntMap(4);
        assertEquals(LongIntMap.MISSING, map.putIfAbsent(-1L, 3));
        assertEquals(3, map.putIfAbsent(-1L, 9));
        assertEquals(3, map.get(-1L));
        assertEquals(LongIntMap.MISSING, map.get(0L));
        assertEquals(LongIntMap.MISSING, map.putIfAbsent(LongIntMap.pair(-5, -6), 1));
        assertEquals(1, map.get(LongIntMap.pair(-5, -6)));
        assertEquals(2, map.size());
    }
}
