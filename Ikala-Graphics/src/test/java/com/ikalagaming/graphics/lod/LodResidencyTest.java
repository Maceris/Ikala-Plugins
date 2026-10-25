package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    /**
     * Over a byte budget only the most needed units are wanted, those whose groups project the most
     * over the threshold, and the budget scale says how much coarser the cut is; within it nothing
     * changes.
     */
    @Test
    void testBudgetKeepsTheMostNeeded() {
        final LodResidency residency = new LodResidency(tree(), new boolean[4]);
        residency.update(new double[][] {{0, 0, 0}}, PIXELS, THRESHOLD, 0, true, 70);
        assertEquals(10 + 20 + 40, residency.wantedBytes());
        assertEquals(1, residency.budgetScale(), 1e-9);
        residency.update(new double[][] {{0, 0, 0}}, PIXELS, THRESHOLD, 0, true, 30);
        assertTrue(residency.isWanted(0));
        assertTrue(residency.isWanted(1));
        assertFalse(residency.isWanted(2));
        assertFalse(residency.isWanted(3), "The leaf, least over the threshold, is left out");
        assertEquals(10 + 20, residency.wantedBytes());
        // The leaf projects to 0.01 * 1000 / 8 = 1.25 times the threshold, the new cutoff
        assertEquals(1.25, residency.budgetScale(), 1e-9);
        // The root is always wanted, whatever the budget
        residency.update(new double[][] {{0, 0, 0}}, PIXELS, THRESHOLD, 0, true, 0);
        assertTrue(residency.isWanted(0));
        assertFalse(residency.isWanted(1));
    }

    /**
     * The budget's cutoff, found by bucketing needs, picks the same units as taking them all the
     * most needed first: every unit over the cutoff, and none past the budget.
     */
    @Test
    void testBudgetCutoffMatchesSortingAll() {
        final java.util.Random random = new java.util.Random(7);
        final List<LodResidency.Unit> units = new ArrayList<>();
        for (int i = 0; i < 2000; ++i) {
            units.add(
                    new LodResidency.Unit(
                            // Some share an error and a distance, so their needs tie
                            i % 7 == 0 ? 0.5 : random.nextDouble() * 2,
                            new double[] {i % 7 == 0 ? 50 : random.nextDouble() * 4000, 0, 0, 1},
                            1 + random.nextInt(1000),
                            new int[0]));
        }
        final double[] eye = {0, 0, 0};
        final double[] needs = new double[units.size()];
        long all = 0;
        for (int i = 0; i < needs.length; ++i) {
            needs[i] =
                    LodCut.projected(units.get(i).error(), units.get(i).bound(), eye, PIXELS)
                            / THRESHOLD;
            all += needs[i] > LodResidency.PREFETCH ? units.get(i).bytes() : 0;
        }
        final List<Integer> byNeed = new ArrayList<>();
        for (int i = 0; i < needs.length; ++i) {
            if (needs[i] > LodResidency.PREFETCH) {
                byNeed.add(i);
            }
        }
        byNeed.sort((a, b) -> Double.compare(needs[b], needs[a]));
        for (long budget : new long[] {all / 2, all / 10, all - 1, 1000, 0}) {
            double cutoff = LodResidency.PREFETCH;
            long spent = 0;
            for (int i : byNeed) {
                spent += units.get(i).bytes();
                if (spent > budget) {
                    cutoff = needs[i];
                    break;
                }
            }
            final LodResidency residency = new LodResidency(units, new boolean[units.size()]);
            residency.update(new double[][] {eye}, PIXELS, THRESHOLD, 0, true, budget);
            for (int i = 0; i < needs.length; ++i) {
                assertEquals(
                        needs[i] > cutoff,
                        residency.isWanted(i),
                        "Budget " + budget + " unit " + i);
            }
        }
    }

    /**
     * With several observers, as a model's instances each see it, a unit is wanted when any of them
     * wants it; with none, only the roots are.
     */
    @Test
    void testAnyObserverWants() {
        final LodResidency residency = new LodResidency(tree(), new boolean[4]);
        residency.update(new double[][] {{5000, 0, 0}, {1000, 0, 0}}, PIXELS, THRESHOLD, 0, true);
        assertTrue(residency.isWanted(0));
        assertFalse(residency.isWanted(1));
        assertTrue(residency.isWanted(2));
        assertFalse(residency.isWanted(3));
        residency.update(new double[0][], PIXELS, THRESHOLD, 0, true);
        assertTrue(residency.isWanted(0));
        assertFalse(residency.isWanted(1));
        assertFalse(residency.isWanted(2));
        assertFalse(residency.isWanted(3));
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

    /**
     * The whole wanted cut is there only once every wanted unit is shown, and not before any is
     * wanted.
     */
    @Test
    void testWantedAllShown() {
        final LodResidency residency = new LodResidency(tree(), new boolean[4]);
        assertFalse(residency.anyWanted());
        assertFalse(residency.wantedAllShown());
        residency.update(new double[] {0, 0, 0}, PIXELS, THRESHOLD, 0, true);
        assertTrue(residency.anyWanted());
        residency.setLoaded(0);
        residency.toShow();
        // The root alone stands in for what is still on its way
        assertFalse(residency.wantedAllShown());
        residency.setLoaded(1);
        residency.setLoaded(3);
        residency.toShow();
        assertTrue(residency.wantedAllShown());
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

    /** A free index is never wanted, loaded, shown or counted, even with streaming off. */
    @Test
    void testFreeIndicesAreSkipped() {
        final List<LodResidency.Unit> units = new ArrayList<>(tree());
        // The far unit is gone, and its index free
        units.set(2, null);
        final LodResidency residency =
                new LodResidency(units, new boolean[] {true, true, true, false});
        assertEquals(4, residency.size());
        assertEquals(3, residency.count());
        assertFalse(residency.isLoaded(2));
        assertFalse(residency.isShown(2));
        residency.update(new double[] {0, 0, 0}, PIXELS, THRESHOLD, 0, false);
        assertFalse(residency.isWanted(2));
        assertEquals(List.of(3), residency.toLoad());
        assertEquals(10 + 20 + 40, residency.totalBytes());
        assertEquals(10 + 20, residency.loadedBytes());
        residency.setLoaded(3);
        assertEquals(List.of(3), residency.toShow());
        assertTrue(residency.wantedAllShown());
    }

    /**
     * The loaded and wanted bytes and the shown count, kept as units change, match counting them
     * again, through loads, shows, updates and evictions, and showing again finds nothing new.
     */
    @Test
    void testKeptTotalsMatchCounting() {
        final LodResidency residency =
                new LodResidency(tree(), new boolean[] {true, false, false, false});
        assertTotals(residency);
        residency.update(new double[] {0, 0, 0}, PIXELS, THRESHOLD, 0, true);
        assertTotals(residency);
        residency.setLoaded(1);
        residency.setLoaded(1);
        residency.setLoaded(3);
        assertEquals(List.of(1, 3), residency.toShow());
        assertTrue(residency.toShow().isEmpty(), "Nothing new to show");
        assertTotals(residency);
        residency.update(new double[] {1000, 0, 0}, PIXELS, THRESHOLD, 0, true);
        assertTotals(residency);
        residency.toEvict(LodResidency.EVICT_DELAY_NANOS + 1, Long.MAX_VALUE);
        assertTotals(residency);
        residency.setLoaded(2);
        assertEquals(List.of(2), residency.toShow());
        assertTotals(residency);
    }

    /**
     * Check a residency's kept totals against counting every unit.
     *
     * @param residency The residency.
     */
    private static void assertTotals(LodResidency residency) {
        long loaded = 0;
        long wanted = 0;
        int shown = 0;
        final List<LodResidency.Unit> units = tree();
        for (int u = 0; u < residency.size(); ++u) {
            loaded += residency.isLoaded(u) ? units.get(u).bytes() : 0;
            wanted += residency.isWanted(u) ? units.get(u).bytes() : 0;
            shown += residency.isShown(u) ? 1 : 0;
        }
        assertEquals(loaded, residency.loadedBytes());
        assertEquals(wanted, residency.wantedBytes());
        assertEquals(shown, residency.shownCount());
    }
}
