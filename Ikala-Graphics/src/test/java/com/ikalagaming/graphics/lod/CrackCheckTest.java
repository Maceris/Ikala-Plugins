package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.SyntheticTerrain;
import com.ikalagaming.graphics.vulkan.LodCullMath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Tests for checking cuts for cracks, whole and column by column. */
@Tag("slow")
class CrackCheckTest {

    /** Section size. */
    private static final int SECTION = SyntheticTerrain.SECTION;

    /** Columns of sections in the test terrain. */
    private static final int COLUMNS = 4;

    /** Views tried. */
    private static final int VIEWS = 12;

    /** Pixels per unit at distance 1: 1080 pixels high, 60 degrees. */
    private static final double PIXELS = 1080 / (2 * Math.tan(Math.toRadians(30)));

    /** The width of a column for the tiled check, smaller than the terrain. */
    private static final double COLUMN = SECTION;

    /** A DAG of region cells over the test terrain. */
    private static LodDag dag;

    /** The highest level each cluster stands at. */
    private static int[] highest;

    /** Build the DAG once. */
    @BeforeAll
    static void build() {
        final LodStore store = LodCellFileTest.store(DagTest.terrain(COLUMNS), null);
        store.rebuild();
        dag = store.snapshot();
        highest = new int[dag.clusters().size()];
        for (int i = 0; i < highest.length; ++i) {
            final int group = dag.consumer(i);
            highest[i] = group < 0 ? dag.getLevels() - 1 : dag.groups().get(group).level();
        }
    }

    /**
     * How many of some edges are no longer than a column, so the tiled check sees them.
     *
     * @param edges The edges.
     * @return How many it checks.
     */
    private static long shortOnes(List<Grouping.Edge> edges) {
        long count = 0;
        for (Grouping.Edge edge : edges) {
            final double dx =
                    Float.intBitsToFloat(edge.a().x()) - Float.intBitsToFloat(edge.b().x());
            final double dy =
                    Float.intBitsToFloat(edge.a().y()) - Float.intBitsToFloat(edge.b().y());
            final double dz =
                    Float.intBitsToFloat(edge.a().z()) - Float.intBitsToFloat(edge.b().z());
            if (dx * dx + dy * dy + dz * dz <= COLUMN * COLUMN) {
                ++count;
            }
        }
        return count;
    }

    /**
     * A view from around and above the terrain.
     *
     * @param random The randomness.
     * @return The view.
     */
    private static LodCut.View randomView(Random random) {
        final double size = COLUMNS * SECTION;
        return new LodCut.View(
                (random.nextDouble() * 3 - 1) * size,
                random.nextDouble() * size,
                (random.nextDouble() * 3 - 1) * size,
                PIXELS);
    }

    /**
     * Drawing two levels at once, which lie over each other, draws edges twice, whole and column by
     * column, though where faces of different clusters meet at an angle is no overdraw.
     */
    @Test
    void testOverlappingLevelsAreDrawnTwice() {
        final boolean[] drawn = new boolean[dag.clusters().size()];
        for (LodDag.Cluster cluster : dag.clusters()) {
            drawn[cluster.id()] = cluster.level() <= 1;
        }
        final CrackCheck.Result whole = new CrackCheck(dag).check(drawn);
        assertTrue(!whole.overdrawnEdges().isEmpty());
        final CrackCheck.Tiled tiled =
                CrackCheck.checkTiled(dag.clusters(), highest, drawn, COLUMN, List.of());
        assertTrue(tiled.overdrawnEdges() > 0);
    }

    /** Clean cuts are clean column by column too, and both checks count the same edges. */
    @Test
    void testTiledMatchesWholeOnCleanCuts() {
        final CrackCheck whole = new CrackCheck(dag);
        final Random random = new Random(7);
        for (int v = 0; v < VIEWS; ++v) {
            final boolean[] drawn =
                    LodCullMath.select(dag, randomView(random), 0.25 + random.nextDouble() * 16);
            final CrackCheck.Result expected = whole.check(drawn);
            final CrackCheck.Tiled tiled =
                    CrackCheck.checkTiled(dag.clusters(), highest, drawn, COLUMN, List.of());
            assertTrue(expected.isClean());
            assertTrue(tiled.isClean(), "View " + v + ": " + tiled);
            assertEquals(expected.drawnClusters(), tiled.drawnClusters());
            assertTrue(tiled.checkedEdges() > 0);
        }
    }

    /** A cut missing a cluster has the same cracks either way, and excused boxes hide them. */
    @Test
    void testTiledFindsTheSameCracks() {
        final CrackCheck whole = new CrackCheck(dag);
        final Random random = new Random(11);
        int broken = 0;
        for (int v = 0; v < VIEWS; ++v) {
            final boolean[] drawn = LodCullMath.select(dag, randomView(random), 1);
            // Leave out one drawn cluster that something else meets
            int dropped = -1;
            for (int i = 0; i < drawn.length && dropped < 0; ++i) {
                if (drawn[i] && random.nextInt(4) == 0) {
                    dropped = i;
                }
            }
            if (dropped < 0) {
                continue;
            }
            drawn[dropped] = false;
            final CrackCheck.Result expected = whole.check(drawn);
            final CrackCheck.Tiled tiled =
                    CrackCheck.checkTiled(dag.clusters(), highest, drawn, COLUMN, List.of());
            assertEquals(shortOnes(expected.openEdges()), tiled.openEdges(), "View " + v);
            assertEquals(shortOnes(expected.overdrawnEdges()), tiled.overdrawnEdges(), "View " + v);
            if (tiled.openEdges() > 0) {
                ++broken;
                assertTrue(!tiled.examples().isEmpty());
                // The whole terrain excused leaves nothing
                final double big = COLUMNS * SECTION * 4.0;
                final CrackCheck.Tiled excused =
                        CrackCheck.checkTiled(
                                dag.clusters(),
                                highest,
                                drawn,
                                COLUMN,
                                List.of(new double[] {-big, -big, -big, big, big, big}));
                assertEquals(0, excused.openEdges());
                assertEquals(tiled.openEdges(), excused.excusedEdges());
            }
        }
        assertTrue(broken > 0, "Some cut was broken");
    }
}
