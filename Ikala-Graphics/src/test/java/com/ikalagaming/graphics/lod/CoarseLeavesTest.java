package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.vulkan.LodCullMath;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for leaves that stand for a surface coarsely, with an error of their own. */
class CoarseLeavesTest {

    /** The coarse leaves' error, in world units. */
    private static final double LEAF_ERROR = 2;

    /** Options for a test build: two threads, no visibility. */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0.5, false, 2, false, 0, false);

    /**
     * A copy of a mesh with a leaf error.
     *
     * @param mesh The mesh.
     * @param error Its error.
     * @return The copy.
     */
    private static LodMesh withError(LodMesh mesh, double error) {
        final LodMesh.Builder builder = LodMesh.builder(mesh.getOrigin().clone()).leafError(error);
        final int[] vertices = new int[mesh.getVertexCount()];
        for (int v = 0; v < vertices.length; ++v) {
            vertices[v] =
                    builder.vertex(mesh.position(v, 0), mesh.position(v, 1), mesh.position(v, 2));
        }
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            builder.triangle(
                    vertices[mesh.corner(t, 0)],
                    vertices[mesh.corner(t, 1)],
                    vertices[mesh.corner(t, 2)],
                    mesh.material(t));
        }
        return builder.build();
    }

    /**
     * Coarse terrain.
     *
     * @return Its sections, each with the leaf error.
     */
    private static List<LodMesh> coarse() {
        final List<LodMesh> out = new ArrayList<>();
        for (LodMesh mesh : DagTest.terrain(2)) {
            out.add(withError(mesh, LEAF_ERROR));
        }
        return out;
    }

    /** Coarse leaves carry their error, and every group above them counts from it. */
    @Test
    void testErrorsCountFromLeaves() {
        final LodDag dag = DagBuilder.build(coarse(), OPTIONS);
        int leaves = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() == 0) {
                assertEquals(LEAF_ERROR, cluster.error(), 0, "Leaf " + cluster.id());
                ++leaves;
            }
        }
        assertTrue(leaves > 0);
        assertTrue(!dag.groups().isEmpty(), "Something was simplified");
        for (LodDag.Group group : dag.groups()) {
            assertTrue(
                    group.error() >= LEAF_ERROR, "Group " + group.id() + " error " + group.error());
        }
    }

    /**
     * Up close, where every group's error is far over the threshold, the cut is the coarse leaves,
     * though their own error is over it too: nothing finer is there to draw instead.
     */
    @Test
    void testLeavesDrawnUpClose() {
        final LodDag dag = DagBuilder.build(coarse(), OPTIONS);
        final double[] middle = dag.clusters().get(0).bound();
        final boolean[] drawn =
                LodCullMath.select(dag, new LodCut.View(middle[0], middle[1], middle[2], 1e6), 1);
        int leaves = 0;
        int drawnLeaves = 0;
        int drawnAbove = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() == 0) {
                ++leaves;
                drawnLeaves += drawn[cluster.id()] ? 1 : 0;
            } else if (drawn[cluster.id()]) {
                ++drawnAbove;
            }
        }
        assertEquals(leaves, drawnLeaves, "Every leaf is drawn");
        assertEquals(0, drawnAbove, "Nothing above the leaves is");
    }
}
