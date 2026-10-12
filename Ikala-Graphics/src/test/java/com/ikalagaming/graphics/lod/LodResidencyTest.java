package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for deciding which streaming units are on the GPU. */
class LodResidencyTest {

    /** Pixels per unit at distance 1. */
    private static final double PIXELS = 1000;

    /** The error threshold, in pixels. */
    private static final double THRESHOLD = 1;

    /**
     * A small tree: a root unit, two units under it, and a leaf unit under the first, along x.
     *
     * <pre>
     * 0 root, always wanted
     * 1 near, error 0.1 at x 10       2 far, error 0.1 at x 1000
     * 3 under 1, error 0.01 at x 10
     * </pre>
     *
     * @return The units.
     */
    private static List<LodResidency.Unit> tree() {
        return List.of(
                new LodResidency.Unit(
                        Double.POSITIVE_INFINITY, new double[] {0, 0, 0, 2000}, 10, new int[0]),
                new LodResidency.Unit(0.1, new double[] {10, 0, 0, 5}, 20, new int[] {0}),
                new LodResidency.Unit(0.1, new double[] {1000, 0, 0, 5}, 30, new int[] {0}),
                new LodResidency.Unit(0.01, new double[] {10, 0, 0, 2}, 40, new int[] {1}));
    }

    /** Near units are wanted and far ones aren't, and wanting is closed upward. */
    @Test
    void testWantedNearAndClosedUpward() {
        final LodResidency residency = new LodResidency(tree(), new boolean[4]);
        residency.update(new double[] {0, 0, 0}, PIXELS, THRESHOLD, 0, true);
        assertTrue(residency.isWanted(0));
        assertTrue(residency.isWanted(1));
        assertFalse(residency.isWanted(2));
        assertTrue(residency.isWanted(3));
        assertEquals(10 + 20 + 40, residency.wantedBytes());
    }

    /** Units load parents first and show only once their parents are shown. */
    @Test
    void testLoadsParentsFirst() {
        final LodResidency residency = new LodResidency(tree(), new boolean[4]);
        residency.update(new double[] {0, 0, 0}, PIXELS, THRESHOLD, 0, true);
        assertEquals(List.of(0, 1, 3), residency.toLoad());
        // The leaf arrives first, but waits for the units above it
        residency.setLoaded(3);
        assertTrue(residency.toShow().isEmpty());
        residency.setLoaded(1);
        residency.setLoaded(0);
        assertEquals(List.of(0, 1, 3), residency.toShow());
    }

    /** Unwanted units go after the delay, children first, and not while a child is loaded. */
    @Test
    void testEvictsAfterDelayChildrenFirst() {
        final boolean[] all = {true, true, true, true};
        final LodResidency residency = new LodResidency(tree(), all);
        assertEquals(4, residency.shownCount());
        // Looking far along x: unit 2 is wanted, 1 and 3 aren't
        residency.update(new double[] {1000, 0, 0}, PIXELS, THRESHOLD, 0, true);
        assertFalse(residency.isWanted(1));
        assertTrue(residency.toEvict(1, Long.MAX_VALUE).isEmpty(), "Not before the delay");
        final List<Integer> evicted =
                residency.toEvict(LodResidency.EVICT_DELAY_NANOS + 1, Long.MAX_VALUE);
        assertEquals(List.of(3, 1), evicted, "The leaf goes before its parent");
        assertTrue(residency.isLoaded(2));
        assertFalse(residency.isShown(1));
    }

    /** Over the byte cap, unwanted units go without waiting. */
    @Test
    void testCapEvictsAtOnce() {
        final boolean[] all = {true, true, true, true};
        final LodResidency residency = new LodResidency(tree(), all);
        residency.update(new double[] {1000, 0, 0}, PIXELS, THRESHOLD, 0, true);
        assertEquals(List.of(3, 1), residency.toEvict(0, 50));
    }

    /** With streaming off, everything is wanted. */
    @Test
    void testStreamingOffWantsAll() {
        final LodResidency residency = new LodResidency(tree(), new boolean[4]);
        residency.update(new double[] {1000, 0, 0}, PIXELS, THRESHOLD, 0, false);
        for (int u = 0; u < residency.size(); ++u) {
            assertTrue(residency.isWanted(u));
        }
    }
}
