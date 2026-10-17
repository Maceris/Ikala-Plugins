package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for free standing small parts in level of detail: what their absence costs. */
class FoldSmallPartsTest {

    /**
     * Options for a test build, on two threads, with no visibility and no virtual edges, which
     * would join the parts to the ground instead, as large scenes like Bistro build.
     */
    private static final DagBuilder.Options OPTIONS =
            new DagBuilder.Options(0, false, 2, false, 0, true);

    /** Sections along each side of the test field. */
    private static final int COLUMNS = 4;

    /** Cells along each side of a section. */
    private static final int SECTION = 16;

    /** How high the field's bumps reach. */
    private static final double HEIGHT = 8;

    /** How far above the ground the parts float. */
    private static final float LIFT = 1;

    /** The cubes' side, in meters. */
    private static final float CUBE = 0.5f;

    /** Cells between parts, along each axis. */
    private static final int SPACING = 3;

    /** The ground's material. */
    private static final int GROUND = 0;

    /** The parts' material. */
    private static final int PARTS = 1;

    /**
     * How much more error a group may come to with the parts than the bare field's: their size, or
     * their lift above ground that is itself as far off as the bare field's error. A part grouped
     * away from the ground under it, charged by its distance to what was left, measured over 30 m.
     */
    private static final double PARTS_ERROR = 5;

    /** What stands on the field. */
    enum Shape {
        /** Nothing. */
        NONE,
        /** Small cubes. */
        CUBES
    }

    /**
     * The ground's height at a point.
     *
     * @param x The x.
     * @param z The z.
     * @return The height.
     */
    private static double height(double x, double z) {
        return HEIGHT * Math.sin(x * 0.31) * Math.cos(z * 0.23);
    }

    /**
     * A bumpy field cut into sections, with a small part floating free over it every few cells.
     *
     * @param shape What the parts are.
     * @return The sections.
     */
    static List<LodMesh> field(Shape shape) {
        final List<LodMesh> meshes = new ArrayList<>();
        for (int sx = 0; sx < COLUMNS; ++sx) {
            for (int sz = 0; sz < COLUMNS; ++sz) {
                final LodMesh.Builder builder =
                        LodMesh.builder(new double[] {sx * SECTION, 0, sz * SECTION});
                final int[][] index = new int[SECTION + 1][SECTION + 1];
                for (int x = 0; x <= SECTION; ++x) {
                    for (int z = 0; z <= SECTION; ++z) {
                        index[x][z] =
                                builder.vertex(
                                        x, (float) height(sx * SECTION + x, sz * SECTION + z), z);
                    }
                }
                for (int x = 0; x < SECTION; ++x) {
                    for (int z = 0; z < SECTION; ++z) {
                        builder.triangle(index[x][z], index[x][z + 1], index[x + 1][z], GROUND);
                        builder.triangle(
                                index[x + 1][z], index[x][z + 1], index[x + 1][z + 1], GROUND);
                    }
                }
                for (int x = 1; x < SECTION; x += SPACING) {
                    for (int z = 1; z < SECTION; z += SPACING) {
                        final float y =
                                (float) height(sx * SECTION + x + 0.5, sz * SECTION + z + 0.5)
                                        + LIFT;
                        if (shape == Shape.NONE) {
                            continue;
                        }
                        cube(builder, x + 0.25f, y, z + 0.25f);
                    }
                }
                meshes.add(builder.build());
            }
        }
        return meshes;
    }

    /**
     * Add a closed cube, its faces outward.
     *
     * @param builder The mesh.
     * @param x Its lowest x.
     * @param y Its lowest y.
     * @param z Its lowest z.
     */
    private static void cube(LodMesh.Builder builder, float x, float y, float z) {
        final int[] v = new int[8];
        for (int i = 0; i < 8; ++i) {
            v[i] =
                    builder.vertex(
                            x + ((i & 1) != 0 ? CUBE : 0),
                            y + ((i & 2) != 0 ? CUBE : 0),
                            z + ((i & 4) != 0 ? CUBE : 0));
        }
        final int[][] faces = {
            {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}
        };
        for (int[] f : faces) {
            builder.triangle(v[f[0]], v[f[1]], v[f[2]], PARTS);
            builder.triangle(v[f[0]], v[f[2]], v[f[3]], PARTS);
        }
    }

    /**
     * The largest error of any cluster.
     *
     * @param dag The DAG.
     * @return The error.
     */
    private static double largestError(LodDag dag) {
        double most = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            most = Math.max(most, cluster.error());
        }
        return most;
    }

    /**
     * A part sharing nothing with the ground costs no more than its width once it is gone, however
     * far it ends up grouped from the ground near it.
     */
    @Test
    void testFreePartsMeasuredAgainstWhatIsNear() {
        final double bare = largestError(DagBuilder.build(field(Shape.NONE), OPTIONS));
        final double withParts = largestError(DagBuilder.build(field(Shape.CUBES), OPTIONS));
        assertTrue(
                withParts <= bare + PARTS_ERROR,
                "Largest error " + withParts + " with the parts, " + bare + " without");
    }
}
