package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.SyntheticTerrain;
import com.ikalagaming.graphics.vulkan.LodCullMath;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Region cells whose groups turn fine parts into coverage volumes: what a volume takes must not
 * open a border another group or cell keeps.
 */
@Tag("slow")
class CellVolumesLodTest {

    /** Columns of sections, enough for parent cells. */
    private static final int COLUMNS = 8;

    /** Views tried. */
    private static final int VIEWS = 30;

    /** Pixels per unit at distance 1: 1080 pixels high, 60 degrees. */
    private static final double PIXELS = 1080 / (2 * Math.tan(Math.toRadians(30)));

    /** Averages for every block material, so far groups blend and fold as live worlds do. */
    private static final MaterialAverages AVERAGES =
            m -> new float[] {(m % 4) / 4f, 0.5f, (m % 3) / 3f, 0.5f, 0, 0.5f};

    /** Cuts of terrain built in cells with volumes open no border, column by column. */
    @Test
    void testCutsHaveNoCracks() {
        final LodStore store =
                new LodStore(
                        new double[3],
                        SyntheticTerrain.SECTION,
                        LodStore.DEFAULT_CELL_SECTIONS,
                        LodStore.DEFAULT_STOP_AT,
                        DagBuilder.Options.defaults().withAverages(AVERAGES).withVolumes(true));
        for (LodMesh section : DagTest.blocks(SyntheticTerrain.Recipe.TERRAIN, COLUMNS)) {
            final double[] origin = section.getOrigin();
            store.put(
                    new LodStore.SectionPos(
                            Math.floorDiv((int) origin[0], SyntheticTerrain.SECTION),
                            Math.floorDiv((int) origin[1], SyntheticTerrain.SECTION),
                            Math.floorDiv((int) origin[2], SyntheticTerrain.SECTION)),
                    section);
        }
        store.rebuild();
        final LodDag dag = store.snapshot();
        final int[] highest = new int[dag.clusters().size()];
        final List<CoverageVolume> volumes = new ArrayList<>();
        for (int i = 0; i < highest.length; ++i) {
            final int group = dag.consumer(i);
            highest[i] = group < 0 ? dag.getLevels() - 1 : dag.groups().get(group).level();
            volumes.add(dag.volumeOf(i));
        }
        final double size = COLUMNS * SyntheticTerrain.SECTION;
        final Random random = new Random(3);
        for (int v = 0; v < VIEWS; ++v) {
            final LodCut.View view =
                    new LodCut.View(
                            (random.nextDouble() * 3 - 1) * size,
                            random.nextDouble() * size,
                            (random.nextDouble() * 3 - 1) * size,
                            PIXELS);
            final boolean[] drawn = LodCullMath.select(dag, view, 0.5 + random.nextDouble() * 16);
            final CrackCheck.Tiled cracks =
                    CrackCheck.checkTiled(
                            dag.clusters(),
                            highest,
                            drawn,
                            SyntheticTerrain.SECTION,
                            List.of(),
                            volumes,
                            CrackCheck.TOLERANCE);
            assertTrue(cracks.openEdges() == 0, "View " + v + ": " + cracks);
        }
    }
}
