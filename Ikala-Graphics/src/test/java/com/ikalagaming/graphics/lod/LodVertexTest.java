package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.benchmark.BlockMeshes;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tests for the level of detail vertex format, turning clusters into meshes, and texturing. */
class LodVertexTest {

    /** Grid steps per unit. */
    private static final double STEPS = LodVertex.STEPS_PER_UNIT;

    /** A vertex reads back as it was written. */
    @Test
    void testRoundTrip() {
        final ByteBuffer buffer =
                ByteBuffer.allocate(2 * LodVertex.SIZE).order(ByteOrder.LITTLE_ENDIAN);
        final int[] grid = {-5, 1 << 30, 123_456};
        LodVertex.put(buffer, 1, grid, 40_000, null, 0);
        final int[] back = new int[3];
        LodVertex.position(buffer, 1, back);
        assertArrayEquals(grid, back);
        assertEquals(40_000, LodVertex.material(buffer, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> LodVertex.put(buffer, 0, grid, LodVertex.MAX_MATERIAL + 1, null, 0));
    }

    /** Leaf positions are on the grid already, and positions too far out are refused. */
    @Test
    void testGrid() {
        assertEquals(16 * LodVertex.STEPS_PER_UNIT + 3, LodVertex.grid(16 + 3 / STEPS));
        assertEquals(-1, LodVertex.grid(-1 / STEPS));
        assertThrows(IllegalArgumentException.class, () -> LodVertex.grid(1e7));
    }

    /**
     * A cluster becomes a mesh with its materials kept, no corner more than half a step from where
     * it was, and vertices shared wherever position and material match, whichever way the triangles
     * face.
     */
    @Test
    void testClusterMesh() {
        // Two triangles of a flat square facing up, a third of another material on the same
        // corners, a fourth standing up facing +x, and a fifth with no area
        final float[] corners = {
            0, 0, 0, 0, 0, 1, 1, 0, 1,
            0, 0, 0, 1, 0, 1, 1, 0, 0,
            0, 0, 0, 0, 0, 1, 1, 0, 1,
            0, 0, 0, 0, 1, 0, 0, 0, 1.3f,
            0, 0, 0, 1, 0, 0, 0.0001f, 0, 0
        };
        final ClusterMeshes.Mesh mesh =
                ClusterMeshes.of(
                        new LodDag.Cluster(
                                0,
                                2,
                                corners,
                                new int[] {3, 3, 7, 3, 3},
                                new float[5],
                                Attributes.NONE,
                                1,
                                new double[4],
                                new double[4],
                                -1,
                                -1));
        assertEquals(4, mesh.triangleCount(), "The triangle with no area is dropped");
        // The square's two triangles share two corners, the other material gets its own, and the
        // upright triangle shares the square's corner at the origin
        assertEquals(4 + 3 + 2, mesh.vertexCount());
        final int[] grid = new int[3];
        final Set<Integer> materials = new HashSet<>();
        for (int i = 0; i < mesh.indices().length; ++i) {
            final int v = mesh.indices()[i];
            final int t = i / 3;
            LodVertex.position(mesh.vertices(), v, grid);
            for (int axis = 0; axis < 3; ++axis) {
                final double original = corners[t * 9 + (i % 3) * 3 + axis];
                assertTrue(Math.abs(grid[axis] / STEPS - original) <= 0.5 / STEPS + 1e-9);
            }
            materials.add(LodVertex.material(mesh.vertices(), v));
        }
        assertEquals(Set.of(3, 7), materials);
        assertArrayEquals(new float[] {0, 0, 0}, mesh.min());
        assertEquals(1.3, mesh.max()[2], 1 / STEPS);
    }

    /** The same position in two clusters encodes to the same steps, so they meet exactly. */
    @Test
    void testSharedCornersMatch() {
        final float shared = 37.123_456f;
        final List<ClusterMeshes.Mesh> meshes =
                List.of(
                        ClusterMeshes.of(
                                cluster(new float[] {shared, 1, 2, 40, 1, 2, shared, 5, 2})),
                        ClusterMeshes.of(
                                cluster(new float[] {shared, 1, 2, shared, 5, 2, 20, 9, 9})));
        final Set<String> positions = new HashSet<>();
        for (ClusterMeshes.Mesh mesh : meshes) {
            for (int v = 0; v < mesh.vertexCount(); ++v) {
                final int[] grid = new int[3];
                LodVertex.position(mesh.vertices(), v, grid);
                positions.add(Arrays.toString(grid));
            }
        }
        assertEquals(4, positions.size(), "Two corners are shared exactly");
    }

    /**
     * A one-triangle cluster.
     *
     * @param corners Its corners.
     * @return The cluster.
     */
    private static LodDag.Cluster cluster(float[] corners) {
        return new LodDag.Cluster(
                0,
                5,
                corners,
                new int[] {1},
                new float[1],
                Attributes.NONE,
                1,
                new double[4],
                new double[4],
                -1,
                -1);
    }

    /** Projected texture coordinates agree with the blocks' own on every face of a block. */
    @Test
    void testTexturingMatchesBlocks() {
        final double[] uv = new double[2];
        final float[] point = new float[3];
        final double[] world = new double[3];
        final int[] block = {-7, 3, 12};
        for (int axis = 0; axis < 3; ++axis) {
            for (int sign = -1; sign <= 1; sign += 2) {
                final float[] normal = new float[3];
                normal[axis] = sign;
                for (double s = 0.05; s < 1; s += 0.15) {
                    for (double t = 0.05; t < 1; t += 0.15) {
                        point[axis] = sign > 0 ? 1 : 0;
                        point[(axis + 1) % 3] = (float) s;
                        point[(axis + 2) % 3] = (float) t;
                        for (int i = 0; i < 3; ++i) {
                            world[i] = block[i] + point[i];
                        }
                        LodTexturing.uv(normal, world, uv);
                        final float[] expected = BlockMeshes.uv(axis, sign, point);
                        final String face = axis + " " + sign;
                        assertEquals(expected[0], LodTexturing.fraction(uv[0]), 1e-5, face);
                        assertEquals(expected[1], LodTexturing.fraction(uv[1]), 1e-5, face);
                    }
                }
            }
        }
    }
}
