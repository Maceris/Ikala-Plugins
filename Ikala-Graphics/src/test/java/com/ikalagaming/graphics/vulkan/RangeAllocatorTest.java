package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Handing out and taking back ranges of a shared buffer. */
class RangeAllocatorTest {

    @Test
    void rangesArePackedFromTheStart() {
        RangeAllocator allocator = new RangeAllocator(100, 1000);
        assertEquals(0, allocator.allocate(10));
        assertEquals(10, allocator.allocate(20));
        assertEquals(30, allocator.allocate(5));
        assertEquals(35, allocator.getUsed());
        assertEquals(65, allocator.getLargestFree());
    }

    @Test
    void freedRangesMergeWithBothNeighbors() {
        RangeAllocator allocator = new RangeAllocator(100, 100);
        int a = allocator.allocate(10);
        int b = allocator.allocate(10);
        int c = allocator.allocate(10);
        allocator.allocate(70);
        allocator.free(a, 10);
        allocator.free(c, 10);
        // Two gaps of 10, nothing bigger
        assertEquals(10, allocator.getLargestFree());
        allocator.free(b, 10);
        // One gap of 30, from both sides merging into the middle
        assertEquals(30, allocator.getLargestFree());
        assertEquals(0, allocator.allocate(30));
    }

    @Test
    void theSmallestFittingGapIsReused() {
        RangeAllocator allocator = new RangeAllocator(100, 100);
        int big = allocator.allocate(30);
        allocator.allocate(10);
        int small = allocator.allocate(8);
        allocator.allocate(52);
        allocator.free(big, 30);
        allocator.free(small, 8);
        // Fits both gaps, so takes the smaller one and leaves the big one whole
        assertEquals(small, allocator.allocate(6));
        assertEquals(30, allocator.getLargestFree());
    }

    @Test
    void theSpaceGrowsWhenNothingFits() {
        RangeAllocator allocator = new RangeAllocator(16, 1000);
        allocator.allocate(10);
        // 6 are free at the end, so growing only needs 14 more, but grows to double
        assertEquals(10, allocator.allocate(20));
        assertEquals(32, allocator.getCapacity());
        // Growing more than double when one allocation needs it
        assertEquals(30, allocator.allocate(200));
        assertEquals(230, allocator.getCapacity());
    }

    @Test
    void growthStopsAtTheMaximum() {
        RangeAllocator allocator = new RangeAllocator(16, 40);
        allocator.allocate(16);
        assertEquals(16, allocator.allocate(24));
        assertEquals(40, allocator.getCapacity());
        assertEquals(-1, allocator.allocate(1));
    }

    @Test
    void emptyRangesTakeNoSpace() {
        RangeAllocator allocator = new RangeAllocator(10, 10);
        assertEquals(0, allocator.allocate(0));
        allocator.free(0, 0);
        assertEquals(0, allocator.getUsed());
        assertEquals(0, allocator.allocate(10));
    }

    @Test
    void freeingWhatWasntAllocatedIsRejected() {
        RangeAllocator allocator = new RangeAllocator(10, 10);
        int start = allocator.allocate(4);
        assertThrows(IllegalArgumentException.class, () -> allocator.free(start, 3));
        assertThrows(IllegalArgumentException.class, () -> allocator.free(5, 1));
        allocator.free(start, 4);
        assertThrows(IllegalArgumentException.class, () -> allocator.free(start, 4));
    }
}
