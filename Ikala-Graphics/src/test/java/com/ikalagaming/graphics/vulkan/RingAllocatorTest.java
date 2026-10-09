package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class RingAllocatorTest {

    private static boolean overlaps(RingAllocator.Allocation a, RingAllocator.Allocation b) {
        return a.getOffset() < b.getOffset() + b.getSize()
                && b.getOffset() < a.getOffset() + a.getSize();
    }

    @Test
    void allocatesInOrderWithAlignment() {
        RingAllocator ring = new RingAllocator(100);

        RingAllocator.Allocation first = ring.allocate(10, 16);
        RingAllocator.Allocation second = ring.allocate(10, 16);

        assertEquals(0, first.getOffset());
        assertEquals(16, second.getOffset());
        assertEquals(10, second.getSize());
    }

    @Test
    void failsWhenFullAndRecoversAfterFree() {
        RingAllocator ring = new RingAllocator(64);
        RingAllocator.Allocation first = ring.allocate(32, 1);
        RingAllocator.Allocation second = ring.allocate(32, 1);

        assertNull(ring.allocate(1, 1));

        ring.free(first);
        RingAllocator.Allocation wrapped = ring.allocate(32, 1);
        assertNotNull(wrapped);
        assertEquals(0, wrapped.getOffset(), "Wraps around into the freed space");
        assertNull(ring.allocate(1, 1));

        ring.free(second);
        ring.free(wrapped);
        assertEquals(0, ring.liveCount());
        assertEquals(0, ring.allocate(64, 1).getOffset(), "An empty ring starts over");
    }

    @Test
    void outOfOrderFreeWaitsForOlderAllocations() {
        RingAllocator ring = new RingAllocator(64);
        RingAllocator.Allocation first = ring.allocate(32, 1);
        RingAllocator.Allocation second = ring.allocate(32, 1);

        ring.free(second);
        assertEquals(2, ring.liveCount(), "The second can't be reclaimed before the first");
        assertNull(ring.allocate(1, 1));

        ring.free(first);
        assertEquals(0, ring.liveCount());
        assertNotNull(ring.allocate(64, 1));
    }

    @Test
    void wrapNeedsRoomBeforeTheOldestAllocation() {
        RingAllocator ring = new RingAllocator(100);
        RingAllocator.Allocation first = ring.allocate(40, 1);
        ring.allocate(40, 1);
        ring.free(first);

        assertNull(ring.allocate(41, 1), "Only 40 bytes free at the start, 20 at the end");
        assertEquals(80, ring.allocate(20, 1).getOffset(), "Fits at the end without wrapping");
        assertEquals(0, ring.allocate(40, 1).getOffset());
        assertNull(ring.allocate(1, 1));
    }

    @Test
    void rejectsBadArguments() {
        RingAllocator ring = new RingAllocator(64);

        assertThrows(IllegalArgumentException.class, () -> ring.allocate(0, 16));
        assertThrows(IllegalArgumentException.class, () -> ring.allocate(8, 3));
        assertThrows(IllegalArgumentException.class, () -> new RingAllocator(0));
    }

    @Test
    void randomWorkloadNeverOverlaps() {
        final long capacity = 1000;
        RingAllocator ring = new RingAllocator(capacity);
        Random random = new Random(1234);
        List<RingAllocator.Allocation> live = new ArrayList<>();

        for (int step = 0; step < 20_000; ++step) {
            if (!live.isEmpty() && random.nextInt(3) == 0) {
                ring.free(live.remove(random.nextInt(live.size())));
                continue;
            }
            final long size = 1 + random.nextInt(200);
            final long alignment = 1L << random.nextInt(5);
            RingAllocator.Allocation allocation = ring.allocate(size, alignment);
            if (allocation == null) {
                continue;
            }
            assertEquals(0, allocation.getOffset() % alignment);
            assertTrue(allocation.getOffset() + allocation.getSize() <= capacity);
            for (RingAllocator.Allocation other : live) {
                assertTrue(!overlaps(allocation, other), "Allocations overlap at step " + step);
            }
            live.add(allocation);
        }
    }
}
