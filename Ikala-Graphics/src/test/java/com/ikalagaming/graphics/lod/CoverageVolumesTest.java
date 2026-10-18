package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for far level of detail turning what is finer than its resolution into volumes. */
class CoverageVolumesTest {

    /** Options for a test build, on two threads, no visibility, virtual edges or planar merging. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0, false, 2, false, 0, false);

    /** How many bushes stand along each side of the field. */
    private static final int BUSHES = 8;

    /** A bush's side. */
    private static final float BUSH = 0.4f;

    /** Cells along each side of a bush's faces. */
    private static final int BUSH_CELLS = 6;

    /** How far apart bushes are. */
    private static final float BUSH_SPACING = 7.5f;

    /** How high the bushes float. */
    private static final float BUSH_HEIGHT = 10;

    /**
     * The test field with bushes floating over it: closed boxes whose faces are finely cut, so they
     * have many triangles in little room, as foliage does.
     *
     * @return The sections, then the bushes.
     */
    private static List<LodMesh> fieldWithBushes() {
        final List<LodMesh> meshes =
                new ArrayList<>(FoldSmallPartsTest.field(FoldSmallPartsTest.Shape.NONE));
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        for (int i = 0; i < BUSHES; ++i) {
            for (int j = 0; j < BUSHES; ++j) {
                bush(builder, 2 + i * BUSH_SPACING, BUSH_HEIGHT, 2 + j * BUSH_SPACING);
            }
        }
        meshes.add(builder.build());
        return meshes;
    }

    /**
     * Add a bush: a box with every face cut into cells, facing out.
     *
     * @param builder The mesh.
     * @param x Its lowest x.
     * @param y Its lowest y.
     * @param z Its lowest z.
     */
    static void bush(LodMesh.Builder builder, float x, float y, float z) {
        for (int axis = 0; axis < 3; ++axis) {
            for (int side = 0; side < 2; ++side) {
                final int u = (axis + 1) % 3;
                final int v = (axis + 2) % 3;
                final int[][] index = new int[BUSH_CELLS + 1][BUSH_CELLS + 1];
                for (int a = 0; a <= BUSH_CELLS; ++a) {
                    for (int b = 0; b <= BUSH_CELLS; ++b) {
                        final float[] p = {x, y, z};
                        p[axis] += side * BUSH;
                        p[u] += BUSH * a / BUSH_CELLS;
                        p[v] += BUSH * b / BUSH_CELLS;
                        index[a][b] = builder.vertex(p[0], p[1], p[2]);
                    }
                }
                for (int a = 0; a < BUSH_CELLS; ++a) {
                    for (int b = 0; b < BUSH_CELLS; ++b) {
                        if (side == 1) {
                            builder.triangle(index[a][b], index[a + 1][b], index[a + 1][b + 1], 1);
                            builder.triangle(index[a][b], index[a + 1][b + 1], index[a][b + 1], 1);
                        } else {
                            builder.triangle(index[a][b], index[a + 1][b + 1], index[a + 1][b], 1);
                            builder.triangle(index[a][b], index[a][b + 1], index[a + 1][b + 1], 1);
                        }
                    }
                }
            }
        }
    }

    /**
     * Bushes floating over a field go to volumes far out: some clusters are volumes, with voxels
     * and no triangles, every cluster's error is at least its children's, and the top levels keep
     * fewer triangles than without volumes.
     */
    @Test
    void testFloatingPartsBecomeVolumes() {
        final LodDag plain = DagBuilder.build(fieldWithBushes(), OPTIONS);
        final LodDag dag = DagBuilder.build(fieldWithBushes(), OPTIONS.withVolumes(true));
        int volumes = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            final CoverageVolume volume = dag.volumeOf(cluster.id());
            if (volume != null) {
                ++volumes;
                assertEquals(0, cluster.triangleCount(), "Volume " + cluster.id());
                assertTrue(volume.size() > 0, "Volume " + cluster.id() + " is empty");
            }
        }
        assertTrue(volumes > 0, "No volumes");
        for (LodDag.Group group : dag.groups()) {
            for (int child : group.children()) {
                assertTrue(
                        group.error() >= dag.clusters().get(child).error(),
                        "Group " + group.id() + " under its child " + child);
            }
        }
        final int top = Math.min(plain.getLevels(), dag.getLevels()) - 1;
        assertTrue(
                dag.trianglesAt(top) < plain.trianglesAt(top),
                dag.trianglesAt(top)
                        + " triangles at level "
                        + top
                        + ", "
                        + plain.trianglesAt(top)
                        + " without volumes");
    }

    /**
     * Cut out leaves turn into volumes at once, never simplified as triangles, which would shrink
     * their cards: no cluster above the leaves has any of them.
     */
    @Test
    void testCutOutLeavesNeverSimplified() {
        final LodDag dag =
                DagBuilder.build(
                        fieldWithBushes(), OPTIONS.withCutOut(m -> m == 1).withVolumes(true));
        int volumes = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            volumes += dag.volumeOf(cluster.id()) != null ? 1 : 0;
            if (cluster.level() == 0) {
                continue;
            }
            for (int material : cluster.materials()) {
                assertTrue(material != 1, "Cluster " + cluster.id() + " keeps cut out leaves");
            }
        }
        assertTrue(volumes > 0, "No volumes");
    }
}
