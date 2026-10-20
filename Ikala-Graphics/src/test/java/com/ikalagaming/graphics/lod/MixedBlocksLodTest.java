package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.SyntheticTerrain;
import com.ikalagaming.graphics.vulkan.LodCullMath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Level of detail for mixed block shapes: the jumble recipe's stairs, slabs, lecterns and chests,
 * whose parts meet unwelded and, once virtual edges weld them, in non-manifold junctions
 * everywhere.
 */
@Tag("slow")
class MixedBlocksLodTest {

    /** Columns of sections. */
    private static final int COLUMNS = 2;

    /** Views tried. */
    private static final int VIEWS = 30;

    /** Pixels per unit at distance 1: 1080 pixels high, 60 degrees. */
    private static final double PIXELS = 1080 / (2 * Math.tan(Math.toRadians(30)));

    /** The most a level may keep of the one below while it is large. */
    private static final double HALVING = 0.65;

    /** Triangles under which a level may halve less, as locked borders start to dominate. */
    private static final int SMALL = 1000;

    /** The sections. */
    private static List<LodMesh> sections;

    /** Averages for every block material, so groups fold free parts. */
    private static final MaterialAverages AVERAGES =
            m -> new float[] {(m % 4) / 4f, 0.5f, (m % 3) / 3f, 0.5f, 0, 0.5f};

    /** Bake the sections once. */
    @BeforeAll
    static void bake() {
        sections = DagTest.blocks(SyntheticTerrain.Recipe.JUMBLE, COLUMNS);
    }

    /**
     * Build the sections' DAG.
     *
     * @param volumes Whether fine parts may become coverage volumes.
     * @return The DAG.
     */
    private static LodDag build(boolean volumes) {
        return DagBuilder.build(
                sections,
                DagBuilder.Options.defaults().withAverages(AVERAGES).withVolumes(volumes));
    }

    /** Every large level keeps at most a little over half of the one below, down to one cluster. */
    @Test
    void testLevelsHalve() {
        final LodDag dag = build(false);
        assertTrue(dag.getStopReason().startsWith("one cluster"), dag.getStopReason());
        for (int level = 1; level < dag.getLevels(); ++level) {
            final int below = dag.trianglesAt(level - 1);
            if (below >= SMALL) {
                assertTrue(
                        dag.trianglesAt(level) <= HALVING * below,
                        "Level " + level + ": " + dag.trianglesAt(level) + " of " + below);
            }
        }
    }

    /**
     * Cuts have no gaps wider than a share of the error drawn there, and no edges drawn twice, with
     * fine parts as triangles or as volumes.
     */
    @Test
    void testCutsHaveNoVisibleCracks() {
        for (boolean volumes : new boolean[] {false, true}) {
            final LodDag dag = build(volumes);
            final CrackCheck check = new CrackCheck(dag);
            final int[] highest = new int[dag.clusters().size()];
            final List<CoverageVolume> volumeOf = new ArrayList<>();
            for (int i = 0; i < highest.length; ++i) {
                final int group = dag.consumer(i);
                highest[i] = group < 0 ? dag.getLevels() - 1 : dag.groups().get(group).level();
                volumeOf.add(dag.volumeOf(i));
            }
            final Random random = new Random(7);
            final double size = COLUMNS * SyntheticTerrain.SECTION;
            for (int v = 0; v < VIEWS; ++v) {
                final LodCut.View view =
                        new LodCut.View(
                                (random.nextDouble() * 3 - 1) * size,
                                random.nextDouble() * size,
                                (random.nextDouble() * 3 - 1) * size,
                                PIXELS);
                final boolean[] drawn =
                        LodCullMath.select(dag, view, 0.25 + random.nextDouble() * 16);
                final CrackCheck.Result exact = check.check(drawn);
                final CrackCheck.Result tolerant = check.check(drawn, CrackCheck.TOLERANCE);
                assertTrue(
                        tolerant.isClean(),
                        "Volumes "
                                + volumes
                                + ", view "
                                + v
                                + ": "
                                + tolerant.openEdges().size()
                                + " open, "
                                + tolerant.overdrawnEdges().size()
                                + " drawn twice");
                // The gate's check, column by column, agrees
                final CrackCheck.Tiled tiled =
                        CrackCheck.checkTiled(
                                dag.clusters(),
                                highest,
                                drawn,
                                SyntheticTerrain.SECTION,
                                List.of(),
                                volumeOf,
                                CrackCheck.TOLERANCE);
                assertTrue(tiled.isClean(), "Volumes " + volumes + ", view " + v + ": " + tiled);
                // The tolerance only excuses what the exact check finds
                assertEquals(
                        exact.openEdges().size(),
                        tolerant.nearEdges(),
                        "Volumes " + volumes + ", view " + v);
            }
        }
    }
}
