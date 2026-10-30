package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When a cluster sent again is consumed as it was, so applying it again can be skipped. */
class PieceClusterTest {

    /** What the clusters are, which doesn't matter here. */
    private static final ClusterInfo INFO =
            new ClusterInfo(
                    0,
                    0,
                    -1,
                    -1,
                    0,
                    new double[] {0, 0, 0, 1},
                    new double[] {0, 0, 0, 1},
                    1,
                    new int[] {0});

    /** The parent cell. */
    private static final LodStore.CellKey PARENT = new LodStore.CellKey(1, 2, 3, 4);

    private static LodStore.PieceCluster consumed(
            LodStore.CellKey cell, int group, double error, double[] bound) {
        return new LodStore.PieceCluster(
                0, INFO, null, error, bound, cell, group, null, null, null);
    }

    /** The same group, error and bound is the same consumer, whichever objects hold them. */
    @Test
    void testSameConsumer() {
        final LodStore.PieceCluster before = consumed(PARENT, 5, 0.25, new double[] {1, 2, 3, 4});
        final LodStore.PieceCluster after =
                consumed(new LodStore.CellKey(1, 2, 3, 4), 5, 0.25, new double[] {1, 2, 3, 4});
        assertTrue(after.sameConsumer(before));
        final LodStore.PieceCluster unconsumed =
                consumed(null, -1, Double.POSITIVE_INFINITY, new double[] {0, 0, 0, 1});
        assertTrue(unconsumed.sameConsumer(unconsumed));
    }

    /** Any change in what consumes it, or how, is a different consumer. */
    @Test
    void testChangedConsumer() {
        final double[] bound = {1, 2, 3, 4};
        final LodStore.PieceCluster before = consumed(PARENT, 5, 0.25, bound);
        assertFalse(consumed(PARENT, 6, 0.25, bound).sameConsumer(before), "Group");
        assertFalse(consumed(PARENT, 5, 0.5, bound).sameConsumer(before), "Error");
        assertFalse(
                consumed(PARENT, 5, 0.25, new double[] {1, 2, 3, 5}).sameConsumer(before), "Bound");
        assertFalse(
                consumed(new LodStore.CellKey(2, 1, 1, 2), 5, 0.25, bound).sameConsumer(before),
                "Cell");
        assertFalse(
                consumed(null, -1, Double.POSITIVE_INFINITY, bound).sameConsumer(before),
                "No longer consumed");
    }
}
