package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for the levels a cut's edges are judged at, with a few clusters made by hand. */
class CrackCheckLevelsTest {

    /** The width of a column for the tiled check, wider than any edge here. */
    private static final double COLUMN = 4;

    /** Where the edge in question starts. */
    private static final float[] A = {0, 0, 0};

    /** Where it ends. */
    private static final float[] B = {1, 0, 0};

    /**
     * A one triangle cluster.
     *
     * @param id Its id.
     * @param level Its level.
     * @param c Its third corner, after the edge's two.
     * @return The cluster.
     */
    private static LodDag.Cluster triangle(int id, int level, float[] c) {
        return triangle(id, level, A, B, c);
    }

    /**
     * A one triangle cluster.
     *
     * @param id Its id.
     * @param level Its level.
     * @param a Its first corner.
     * @param b Its second.
     * @param c Its third.
     * @return The cluster.
     */
    private static LodDag.Cluster triangle(int id, int level, float[] a, float[] b, float[] c) {
        final float[] corners = {a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]};
        return new LodDag.Cluster(
                id,
                level,
                corners,
                new int[1],
                new float[] {1},
                new int[0],
                level,
                new double[] {0.5, 0.5, 0.5, 2},
                new double[] {0.5, 0.5, 0.5, 2},
                -1,
                -1);
    }

    /**
     * A wall cluster standing from level 1 up to level 3, whose foot a level 3 cluster closes, is
     * drawn beside a finer neighbor that never had the foot: the foot is open as at the wall's own
     * level, which is no crack.
     */
    @Test
    void testEdgeClosedOnlyAboveIsJudgedAtItsOwnLevel() {
        final List<LodDag.Cluster> clusters =
                List.of(
                        // The wall, consumed at level 3
                        triangle(0, 1, new float[] {0, 1, 0}),
                        // What closes its foot at level 3
                        triangle(1, 3, new float[] {0, 0, 1}),
                        // A finer neighbor beside it, not at the foot
                        triangle(2, 2, B, new float[] {2, 0, 0}, new float[] {1, 0, 1}));
        final int[] highest = {3, 3, 2};
        final boolean[] drawn = {true, false, true};
        final CrackCheck.Tiled tiled =
                CrackCheck.checkTiled(clusters, highest, drawn, COLUMN, List.of());
        assertEquals(0, tiled.openEdges(), tiled.toString());
    }

    /**
     * A wall's foot open in the input, which a level 2 cluster closes where it comes to rest on it,
     * is the input's rim: drawn at level 2 beside a level 1 neighbor that keeps clear of it, as a
     * slab drawn finer than the wall, it is no crack.
     */
    @Test
    void testInputRimClosedAboveIsNoCrack() {
        final List<LodDag.Cluster> clusters =
                List.of(
                        // The wall in the input, its foot open
                        triangle(0, 0, new float[] {0, 1, 0}),
                        // What rests on the foot at level 2
                        triangle(1, 2, new float[] {0, 0, 1}),
                        // The wall at level 2
                        triangle(2, 2, new float[] {0.5f, 1, 0}),
                        // The neighbor drawn finer, clear of the foot
                        triangle(
                                3,
                                1,
                                new float[] {0, 0, 2},
                                new float[] {1, 0, 2},
                                new float[] {0, 0, 3}));
        final int[] highest = {1, 2, 2, 1};
        final boolean[] drawn = {false, false, true, true};
        final CrackCheck.Tiled tiled =
                CrackCheck.checkTiled(clusters, highest, drawn, COLUMN, List.of());
        assertEquals(0, tiled.openEdges(), tiled.toString());
    }

    /** The same wall with a level 1 neighbor sharing its foot left out is still a crack. */
    @Test
    void testEdgeClosedAtItsOwnLevelIsACrack() {
        final List<LodDag.Cluster> clusters =
                List.of(
                        triangle(0, 1, new float[] {0, 1, 0}),
                        // Its neighbor at its own level, which the cut leaves out
                        triangle(1, 1, new float[] {0, 0, -1}));
        final int[] highest = {3, 3};
        final boolean[] drawn = {true, false};
        final CrackCheck.Tiled tiled =
                CrackCheck.checkTiled(clusters, highest, drawn, COLUMN, List.of());
        assertEquals(1, tiled.openEdges(), tiled.toString());
    }
}
