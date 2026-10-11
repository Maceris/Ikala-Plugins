package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Tests for following triangles down to the input and measuring how faithful each level is. */
class FidelityTest {

    /** Options for a test build, on two threads, keeping the lineage. */
    private static final DagBuilder.Options OPTIONS = new DagBuilder.Options(0.5, true, 2, true, 0);

    /**
     * A flat square grid on the xz plane, material 0 on the low x half and 1 on the high.
     *
     * @param size Quads along each side.
     * @return The mesh.
     */
    private static LodMesh halves(int size) {
        LodMesh.Builder builder = LodMesh.builder(new double[3]);
        for (int x = 0; x < size; ++x) {
            for (int z = 0; z < size; ++z) {
                final int a = builder.vertex(x, 0, z);
                final int b = builder.vertex(x + 1, 0, z);
                final int c = builder.vertex(x + 1, 0, z + 1);
                final int d = builder.vertex(x, 0, z + 1);
                final int material = x < size / 2 ? 0 : 1;
                builder.triangle(a, d, c, material);
                builder.triangle(a, c, b, material);
            }
        }
        return builder.build();
    }

    /** Leaves are the input, so they match it exactly. */
    @Test
    void testLeavesAreExact() {
        final Fidelity fidelity = Fidelity.measure(DagBuilder.build(DagTest.terrain(2), OPTIONS));
        final Fidelity.Level leaves = fidelity.levels().get(0);
        assertEquals(0, leaves.mismatch());
        assertEquals(0, leaves.maxDistance(), 1e-5);
    }

    /** Every point on any level lands on a leaf, near where it started on a flat input. */
    @Test
    void testPointsLandOnLeaves() {
        final LodDag dag = DagBuilder.build(List.of(halves(48)), OPTIONS);
        assertTrue(dag.getLevels() >= 3, dag.getLevels() + " levels");
        final double[] a = new double[3];
        final double[] b = new double[3];
        final double[] c = new double[3];
        for (LodDag.Cluster cluster : dag.clusters()) {
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                Lineage.corners(cluster, t, a, b, c);
                final double[] point = new double[3];
                for (int axis = 0; axis < 3; ++axis) {
                    point[axis] = (a[axis] + b[axis] + c[axis]) / 3;
                }
                final Lineage.Landing landing = dag.getLineage().toLeaf(cluster.id(), t, point);
                assertEquals(0, dag.clusters().get(landing.leaf()).level());
                assertEquals(0, point[1], 1e-5, "Points stay on the plane");
            }
        }
    }

    /** Two halves keep their shares, and the material changes only near the seam. */
    @Test
    void testHalvesKeepTheirShares() {
        final Fidelity fidelity = Fidelity.measure(DagBuilder.build(List.of(halves(48)), OPTIONS));
        for (Fidelity.Level level : fidelity.levels()) {
            assertTrue(level.meanDistance() < 1e-4, "Level " + level.level() + " moved");
            for (int material = 0; material < 2; ++material) {
                final double[] share = level.shares().get(material);
                assertEquals(0.5, share[2], 0.02, "Level " + level.level() + " found shares");
                assertEquals(0.5, share[0], 0.15, "Level " + level.level() + " assigned shares");
            }
            if (level.level() <= 2) {
                assertTrue(level.mismatch() < 0.15, "Level " + level.level() + " mismatch");
            }
        }
        System.out.print(fidelity.report(material -> material == 0 ? "left" : "right"));
    }

    /** Measuring needs the lineage. */
    @Test
    void testNeedsTheLineage() {
        final LodDag dag =
                DagBuilder.build(
                        List.of(halves(8)), new DagBuilder.Options(0.5, true, 2, false, 0));
        assertThrows(IllegalArgumentException.class, () -> Fidelity.measure(dag));
    }

    /** Log the terrain's fidelity, for judging how materials are assigned. */
    @Test
    void testTerrainLogged() {
        final LodDag dag = DagBuilder.build(DagTest.terrain(4), OPTIONS);
        final long start = System.nanoTime();
        final Fidelity fidelity = Fidelity.measure(dag);
        System.out.printf("Measured in %.0f ms%n", (System.nanoTime() - start) / 1e6);
        System.out.print(
                fidelity.report(
                        material ->
                                switch (material) {
                                    case 1 -> "stone";
                                    case 2 -> "grass";
                                    case 3 -> "slab";
                                    default -> "m" + material;
                                }));
    }
}
